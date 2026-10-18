package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarConfigStore;
import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import codes.castled.crowbar.MarkerFade;
import com.mojang.datafixers.util.Either;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.waypoint.TrackedWaypoint;
import net.minecraft.world.waypoint.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        if (CrowBarState.shouldCancelVanillaLocatorBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorAddonsInSelfView(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
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

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return uuid.toString();
        }

        PlayerListEntry entry = networkHandler.getPlayerListEntry(uuid);
        String name = entry == null ? uuid.toString() : entry.getProfile().getName();
        CROWBAR$UUID_NAME_CACHE.put(uuid, name);
        return name;
    }

    /**
     * Overlays faces and labels on the dots vanilla just drew.
     *
     * <p>Labels go into {@link CrowBarHudRenderer}'s queue rather than straight to the screen, so they
     * share overlap culling, focus and styling with CrowBar's own markers.
     */
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
        World world = cameraEntity.getWorld();
        Camera camera = client.gameRenderer.getCamera();
        TrackedWaypoint.YawProvider yawProvider = new TrackedWaypoint.YawProvider() {
            @Override
            public net.minecraft.util.math.Vec3d getCameraPos() {
                return camera.getPos();
            }
            @Override
            public float getCameraYaw() {
                return camera.getYaw();
            }
        };
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int locatorBarY = ((LocatorBar) (Object) this).getCenterY(client.getWindow());

        client.player.networkHandler.getWaypointHandler().forEachWaypoint(cameraEntity, waypoint -> {
            if (crowbar$isSelfWaypoint(waypoint, cameraEntity)) {
                return;
            }

            double yaw = waypoint.getRelativeYaw(world, yawProvider);
            if (yaw <= -61.0D || yaw > 60.0D) {
                return;
            }

            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(yaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            if (markerX < 0 || markerX >= screenWidth || markerY < 0 || markerY >= screenHeight) {
                return;
            }

            Either<UUID, String> source = waypoint.getSource();
            UUID ownerUuid = source == null ? null : source.left().orElse(null);
            // Claim markers are drawn from claim data instead, which carries the name.
            if (CrowBarState.isClaimMarker(ownerUuid)) {
                return;
            }
            // Only players have a tab list entry. Other UUID waypoints are marker entities or
            // datapack waypoints with no face or name, so they keep vanilla's dot and no label
            // rather than a raw UUID.
            boolean isPlayer = ownerUuid != null && client.player.networkHandler.getPlayerListEntry(ownerUuid) != null;
            String ownerName = source == null ? null : source.map(uuid -> isPlayer ? this.crowbar$getName(uuid) : null, name -> name);
            double distance = crowbar$getDistanceFromWaypoint(waypoint, cameraEntity, world);
            int size = crowbar$calculateSizeFromDistance((float) distance, 9) / 100;
            int centerX = markerX + 4;
            int centerY = markerY + 4;

            if (isPlayer) {
                Waypoint.Config config = waypoint.getConfig();
                int vanillaColor = config.color.orElseGet(
                        () -> ColorHelper.withBrightness(ColorHelper.withAlpha(255, ownerUuid.hashCode()), 0.9F));
                float spriteDistance = MathHelper.sqrt((float) waypoint.squaredDistanceTo(cameraEntity));
                Identifier vanillaSprite = client.getWaypointStyleAssetManager().get(config.style).getSpriteForDistance(spriteDistance);
                CrowBarHudRenderer.renderVanillaPlayerOverlay(context, ownerUuid, centerX, centerY, size, distance, vanillaColor, vanillaSprite);
            }

            String label = CrowBarHudRenderer.labelText(CrowBarState.nameTagsEnabled ? ownerName : null, distance);
            CrowBarHudRenderer.queueLabel(label, centerX, centerY - size / 2 - 12,
                    MarkerFade.alpha(CrowBarConfigStore.effective(), distance));
        });
    }

    @Unique
    private boolean crowbar$isSelfWaypoint(TrackedWaypoint waypoint, Entity cameraEntity) {
        Either<UUID, String> source = waypoint.getSource();
        return source != null && source.left()
                .map(uuid -> uuid.equals(cameraEntity.getUuid()))
                .orElse(false);
    }

    /** Blocks to the waypoint's owner, or {@code -1} when no position source knows where they are. */
    @Unique
    private double crowbar$getDistanceFromWaypoint(TrackedWaypoint waypoint, Entity cameraEntity, World world) {
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
                    return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
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
}
