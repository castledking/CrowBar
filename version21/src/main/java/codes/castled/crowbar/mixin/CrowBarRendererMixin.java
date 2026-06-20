package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import com.mojang.datafixers.util.Either;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.world.waypoint.TrackedWaypoint;
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

@Environment(EnvType.CLIENT)
@Mixin(LocatorBar.class)
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

    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorBarInSelfView(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrowBarState.isXpBarVisible()) return;
        if (CrowBarState.isExternalRenderSuppressed()) {
            if (!CrowBarState.shouldKeepVanillaLocatorBarDuringExternalSuppression()) {
                ci.cancel();
            }
            return;
        }
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        if (CrowBarState.alliumDataReceived) {
            ci.cancel();
            return;
        }
        if (!CrowBarState.isIntegratedServer) return;
        Entity cameraEntity = MinecraftClient.getInstance().getCameraEntity();
        if (cameraEntity != null && CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUuid())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorAddonsInSelfView(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrowBarState.isXpBarVisible()) return;
        if (CrowBarState.isExternalRenderSuppressed()) {
            if (!CrowBarState.shouldKeepVanillaLocatorBarDuringExternalSuppression()) {
                ci.cancel();
            }
            return;
        }
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        if (CrowBarState.alliumDataReceived) {
            ci.cancel();
            return;
        }
        if (!CrowBarState.isIntegratedServer) return;
        Entity cameraEntity = MinecraftClient.getInstance().getCameraEntity();
        if (cameraEntity != null && CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUuid())) {
            ci.cancel();
        }
    }

    @Unique
    private String crowbar$getName(UUID uuid) {
        String cached = CROWBAR$UUID_NAME_CACHE.get(uuid);
        if (cached != null) {
            return cached;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return uuid.toString();
        }

        PlayerListEntry entry = networkHandler.getPlayerListEntry(uuid);
        String name = entry == null ? uuid.toString() : entry.getProfile().name();
        CROWBAR$UUID_NAME_CACHE.put(uuid, name);
        return name;
    }

    @Inject(method = "renderAddons", at = @At("TAIL"))
    private void crowbar$renderLocatorAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrowBarState.isExternalRenderSuppressed()) {
            return;
        }
        if (CrowBarState.isXpBarVisible()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getCameraEntity() == null) {
            return;
        }

        Entity cameraEntity = client.getCameraEntity();
        World world = cameraEntity.getEntityWorld();
        Camera camera = client.gameRenderer.getCamera();
        EntityTickProgress tickProgress = entity ->
                tickCounter.getTickProgress(!world.getTickManager().shouldSkipTick(entity));
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int centerX = screenWidth / 2;
        int locatorBarY = ((LocatorBar) (Object) this).getCenterY(client.getWindow());
        List<WaypointRenderData> waypoints = new ArrayList<>();

        client.player.networkHandler.getWaypointHandler().forEachWaypoint(cameraEntity, waypoint -> {
            if (crowbar$isSelfWaypoint(waypoint, cameraEntity)) {
                return;
            }

            double yaw = waypoint.getRelativeYaw(world, camera, tickProgress);
            if (yaw <= -61.0D || yaw > 60.0D) {
                return;
            }

            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(yaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            String ownerName = "Unknown";

            Either<UUID, String> source = waypoint.getSource();
            if (source != null) {
                ownerName = source.map(this::crowbar$getName, name -> name);
            }

            boolean valid = markerX >= 0 && markerX < screenWidth && markerY >= 0 && markerY < screenHeight;
            UUID ownerUuid = source == null ? null : source.left().orElse(null);
            double distance = crowbar$getDistanceFromWaypoint(waypoint, cameraEntity, world, tickProgress);
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
        Either<UUID, String> source = waypoint.getSource();
        return source != null && source.left()
                .map(uuid -> uuid.equals(cameraEntity.getUuid()))
                .orElse(false);
    }

    @Unique
    private double crowbar$getDistanceFromWaypoint(TrackedWaypoint waypoint, Entity cameraEntity, World world, EntityTickProgress tickProgress) {
        try {
            // Try to get entity from waypoint source
            Either<UUID, String> source = waypoint.getSource();
            if (source != null && source.left().isPresent()) {
                UUID uuid = source.left().get();
                
                // Use unified position lookup (Allium data, IntegratedServer, or entity fallback)
                var playerPos = CrowBarState.getPlayerPos(uuid);
                if (playerPos.isPresent()) {
                    net.minecraft.util.math.Vec3d pos = playerPos.get();
                    double deltaX = pos.x - cameraEntity.getX();
                    double deltaY = pos.y - cameraEntity.getY();
                    double deltaZ = pos.z - cameraEntity.getZ();
                    double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                    return distance;
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Unique
    private int crowbar$calculateSizeFromDistance(float distance, int baseSize) {
        // Shared helper for scaling locator dot/skin size based on distance
        // Uses vanilla waypoint style behavior (near/far distance interpolation)
        // Returns size in hundredths (multiply by 0.01f to get pixels)
        // Note: WaypointStyle API not available in 1.21.11, using fallback constants
        if (distance <= 0) return baseSize * 100;
        
        float progress = 1.0F - MathHelper.clamp(
                (distance - CROWBAR_FALLBACK_NEAR_DISTANCE) /
                (CROWBAR_FALLBACK_FAR_DISTANCE - CROWBAR_FALLBACK_NEAR_DISTANCE),
                0.0F,
                1.0F
        );

        return (int) MathHelper.lerp(
                progress,
                CROWBAR_MIN_DOT_SIZE_HUNDREDTHS,
                baseSize * 100
        );
    }

    @Unique
    private void crowbar$drawWaypoint(DrawContext context, TextRenderer textRenderer, WaypointRenderData data, int color) {
        // Render skin for vanilla-visible waypoints when skins are enabled
        if (CrowBarState.skinsEnabled && data.ownerUuid() != null) {
            int sizeHundredths = crowbar$calculateSizeFromDistance((float) data.distance(), 9);
            int size = sizeHundredths / 100;
            int centerX = data.x() + 4;
            int centerY = data.y() + 4;
            
            // Draw skin if available
            SkinTextures skin = crowbar$getSkin(data.ownerUuid());
            if (skin != null) {
                PlayerSkinDrawer.draw(context, skin, centerX - size / 2, centerY - size / 2, size);
            }
        }
        
        // Handle nametags/distance
        if (CrowBarState.nameTagsEnabled) {
            int textWidth = textRenderer.getWidth(data.ownerName());
            int textX = data.x() - textWidth / 2;
            int textY = data.y() - 10;
            context.drawTextWithShadow(textRenderer, data.ownerName(), textX, textY, color);

            if (CrowBarState.showDistance) {
                if (data.distance() > 0) {
                    String distanceText = String.format("%.0fm", data.distance());
                    int distanceX = textX + textWidth + 4;
                    int distanceY = textY;
                    context.drawTextWithShadow(textRenderer, distanceText, distanceX, distanceY, color);
                } else {
                    String distanceText = "?m";
                    int distanceX = textX + textWidth + 4;
                    int distanceY = textY;
                    context.drawTextWithShadow(textRenderer, distanceText, distanceX, distanceY, color);
                }
            }
        } else if (CrowBarState.showDistance) {
            if (data.distance() > 0) {
                String distanceText = String.format("%.0fm", data.distance());
                int distanceWidth = textRenderer.getWidth(distanceText);
                int distanceX = data.x() - distanceWidth / 2;
                int distanceY = data.y() - 10;
                context.drawTextWithShadow(textRenderer, distanceText, distanceX, distanceY, color);
            } else {
                String distanceText = "?m";
                int distanceWidth = textRenderer.getWidth(distanceText);
                int distanceX = data.x() - distanceWidth / 2;
                int distanceY = data.y() - 10;
                context.drawTextWithShadow(textRenderer, distanceText, distanceX, distanceY, color);
            }
        }
    }

    @Unique
    private record WaypointRenderData(int x, int y, String ownerName, UUID ownerUuid, boolean valid, double distance) {
    }

    @Unique
    private SkinTextures crowbar$getSkin(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) return null;
        PlayerListEntry entry = networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            return entry.getSkinTextures();
        }
        if (client.player != null && client.player.getUuid().equals(uuid)) {
            return client.player.getSkin();
        }
        return null;
    }
}
