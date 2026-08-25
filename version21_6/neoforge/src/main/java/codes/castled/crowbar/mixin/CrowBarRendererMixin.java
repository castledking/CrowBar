package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(LocatorBarRenderer.class)
public abstract class CrowBarRendererMixin {
    @Unique
    private static final ConcurrentHashMap<UUID, String> CROWBAR$UUID_NAME_CACHE = new ConcurrentHashMap<>();

    // Fallback constants for waypoint style scaling (when WaypointStyle API is unavailable)
    @Unique
    private static final float CROWBAR_FALLBACK_NEAR_DISTANCE = 128.0F;
    @Unique
    private static final float CROWBAR_FALLBACK_FAR_DISTANCE = 256.0F;
    @Unique
    private static final int CROWBAR_MIN_DOT_SIZE_HUNDREDTHS = 500;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorBarInSelfView(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.shouldCancelVanillaLocatorBar()) {
            ci.cancel();
        }
    }

    @Unique
    private String crowbar$getName(UUID uuid) {
        String cached = CROWBAR$UUID_NAME_CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }

        Minecraft client = Minecraft.getInstance();
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) {
            return uuid.toString();
        }

        PlayerInfo entry = networkHandler.getPlayerInfo(uuid);
        String name = entry == null ? uuid.toString() : entry.getProfile().getName();
        CROWBAR$UUID_NAME_CACHE.put(uuid, name);
        return name;
    }

    /** Draw skins, name tags, and distances over vanilla's locator dots and arrows. */
    @Inject(method = "render", at = @At("TAIL"))
    private void crowbar$renderLocatorAddons(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.isExternalRenderSuppressed()) {
            return;
        }
        if (CrowBarState.isXpBarVisible()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getCameraEntity() == null) {
            return;
        }

        Entity cameraEntity = client.getCameraEntity();
        Level world = cameraEntity.level();
        Camera camera = client.gameRenderer.getMainCamera();
        TickRateManager tickRateManager = world.tickRateManager();
        Font textRenderer = client.font;
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int centerX = screenWidth / 2;
        int locatorBarY = ((ContextualBarRenderer) (Object) this).top(client.getWindow());
        List<WaypointRenderData> waypoints = new ArrayList<>();

        client.player.connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            if (crowbar$isSelfWaypoint(waypoint, cameraEntity)) {
                return;
            }

            double yaw = waypoint.yawAngleToCamera(world, camera);
            if (yaw <= -61.0D || yaw > 60.0D) {
                return;
            }

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(yaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            String ownerName = "Unknown";

            Either<UUID, String> source = waypoint.id();
            if (source != null) {
                ownerName = source.map(this::crowbar$getName, name -> name);
            }

            boolean valid = markerX >= 0 && markerX < screenWidth && markerY >= 0 && markerY < screenHeight;
            UUID ownerUuid = source == null ? null : source.left().orElse(null);
            double distance = crowbar$getDistanceFromWaypoint(waypoint, cameraEntity);
            waypoints.add(new WaypointRenderData(markerX, markerY, ownerName, ownerUuid, valid, distance));
        });

        WaypointRenderData closest = waypoints.stream()
                .filter(WaypointRenderData::valid)
                .min(Comparator.comparingInt(data -> Math.abs(data.x() - centerX)))
                .orElse(null);

        for (WaypointRenderData data : waypoints) {
            if (!data.valid() || data == closest) {
                continue;
            }
            crowbar$drawWaypoint(context, textRenderer, data, 0x70FFFFFF);
        }

        if (closest != null && closest.valid()) {
            crowbar$drawWaypoint(context, textRenderer, closest, 0xFFFFFFFF);
        }
    }

    @Unique
    private boolean crowbar$isSelfWaypoint(TrackedWaypoint waypoint, Entity cameraEntity) {
        Either<UUID, String> source = waypoint.id();
        return source != null && source.left()
                .map(uuid -> uuid.equals(cameraEntity.getUUID()))
                .orElse(false);
    }

    /**
     * Distance for the label. Vanilla waypoints only cover nearby entities, but Allium positions
     * (when available) give the true distance beyond render range.
     */
    @Unique
    private double crowbar$getDistanceFromWaypoint(TrackedWaypoint waypoint, Entity cameraEntity) {
        try {
            Either<UUID, String> source = waypoint.id();
            if (source != null && source.left().isPresent()) {
                UUID uuid = source.left().get();

                var playerPos = CrowBarState.getPlayerPos(uuid);
                if (playerPos.isPresent()) {
                    net.minecraft.world.phys.Vec3 pos = playerPos.get();
                    double deltaX = pos.x - cameraEntity.getX();
                    double deltaY = pos.y - cameraEntity.getY();
                    double deltaZ = pos.z - cameraEntity.getZ();
                    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private void crowbar$drawWaypoint(GuiGraphics context, Font textRenderer, WaypointRenderData data, int color) {
        // Render skin for vanilla-visible waypoints when skins are enabled
        if (CrowBarState.skinsEnabled && data.ownerUuid() != null) {
            int sizeHundredths = crowbar$calculateSizeFromDistance((float) data.distance(), 9);
            int size = sizeHundredths / 100;
            int centerX = data.x() + 4;
            int centerY = data.y() + 4;

            PlayerSkin skin = crowbar$getSkin(data.ownerUuid());
            if (skin != null) {
                PlayerFaceRenderer.draw(context, skin, centerX - size / 2, centerY - size / 2, size);
            }
        }

        // Handle nametags/distance
        if (CrowBarState.nameTagsEnabled) {
            int textWidth = textRenderer.width(data.ownerName());
            int textX = data.x() - textWidth / 2;
            int textY = data.y() - 10;
            context.drawString(textRenderer, data.ownerName(), textX, textY, color);

            if (CrowBarState.showDistance) {
                String distanceText = data.distance() > 0 ? String.format("%.0fm", data.distance()) : "?m";
                int distanceX = textX + textWidth + 4;
                int distanceY = textY;
                context.drawString(textRenderer, distanceText, distanceX, distanceY, color);
            }
        } else if (CrowBarState.showDistance) {
            String distanceText = data.distance() > 0 ? String.format("%.0fm", data.distance()) : "?m";
            int distanceWidth = textRenderer.width(distanceText);
            int distanceX = data.x() - distanceWidth / 2;
            int distanceY = data.y() - 10;
            context.drawString(textRenderer, distanceText, distanceX, distanceY, color);
        }
    }

    @Unique
    private int crowbar$calculateSizeFromDistance(float distance, int baseSize) {
        // Shared helper for scaling locator dot/skin size based on distance
        // Uses vanilla waypoint style behavior (near/far distance interpolation)
        // Returns size in hundredths (multiply by 0.01f to get pixels)
        if (distance <= 0) return baseSize * 100;

        float progress = 1.0F - Mth.clamp(
                (distance - CROWBAR_FALLBACK_NEAR_DISTANCE) /
                (CROWBAR_FALLBACK_FAR_DISTANCE - CROWBAR_FALLBACK_NEAR_DISTANCE),
                0.0F,
                1.0F
        );

        return (int) Mth.lerp(
                progress,
                CROWBAR_MIN_DOT_SIZE_HUNDREDTHS,
                baseSize * 100
        );
    }

    @Unique
    private record WaypointRenderData(int x, int y, String ownerName, UUID ownerUuid, boolean valid, double distance) {
    }

    @Unique
    private PlayerSkin crowbar$getSkin(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener networkHandler = client.getConnection();
        if (networkHandler == null) return null;
        PlayerInfo entry = networkHandler.getPlayerInfo(uuid);
        if (entry != null) {
            return entry.getSkin();
        }
        if (client.player != null && client.player.getUUID().equals(uuid)) {
            return client.player.getSkin();
        }
        return null;
    }
}
