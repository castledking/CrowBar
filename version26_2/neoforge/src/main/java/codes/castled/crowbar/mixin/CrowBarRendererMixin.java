package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarConfigStore;
import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import codes.castled.crowbar.MarkerFade;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.Camera;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorBarInSelfView(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
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
        String name = entry == null ? uuid.toString() : entry.getProfile().name();
        CROWBAR$UUID_NAME_CACHE.put(uuid, name);
        return name;
    }

    /**
     * Overlays faces and labels on the dots vanilla just drew.
     *
     * <p>Labels go into {@link CrowBarHudRenderer}'s queue rather than straight to the screen, so they
     * share overlap culling, focus and styling with CrowBar's own markers.
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crowbar$renderLocatorAddons(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
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
        Camera camera = client.gameRenderer.mainCamera();
        PartialTickSupplier tickProgress = entity ->
                tickCounter.getGameTimeDeltaPartialTick(!world.tickRateManager().isEntityFrozen(entity));
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int locatorBarY = ((ContextualBar) (Object) this).top(client.getWindow());

        client.player.connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            if (crowbar$isSelfWaypoint(waypoint, cameraEntity)) {
                return;
            }

            double yaw = waypoint.yawAngleToCamera(world, camera, tickProgress);
            if (yaw <= -61.0D || yaw > 60.0D) {
                return;
            }

            int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + Mth.floor(yaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            if (markerX < 0 || markerX >= screenWidth || markerY < 0 || markerY >= screenHeight) {
                return;
            }

            Either<UUID, String> source = waypoint.id();
            UUID ownerUuid = source == null ? null : source.left().orElse(null);
            // Claim markers are drawn from claim data instead, which carries the name.
            if (CrowBarState.isClaimMarker(ownerUuid)) {
                return;
            }
            // Only players have a tab list entry. Other UUID waypoints are marker entities or
            // datapack waypoints with no face or name, so they keep vanilla's dot and no label
            // rather than a raw UUID.
            boolean isPlayer = ownerUuid != null && client.player.connection.getPlayerInfo(ownerUuid) != null;
            String ownerName = source == null ? null : source.map(uuid -> isPlayer ? this.crowbar$getName(uuid) : null, name -> name);
            double distance = crowbar$getDistanceFromWaypoint(waypoint, cameraEntity);
            int size = crowbar$calculateSizeFromDistance((float) distance, 9) / 100;
            int centerX = markerX + 4;
            int centerY = markerY + 4;

            if (isPlayer) {
                Waypoint.Icon icon = waypoint.icon();
                int vanillaColor = icon.color.orElseGet(
                        () -> ARGB.setBrightness(ARGB.color(255, ownerUuid.hashCode()), 0.9F));
                float spriteDistance = Mth.sqrt((float) waypoint.distanceSquared(cameraEntity));
                Identifier vanillaSprite = client.gui.hud.getWaypointStyles().get(icon.style).sprite(spriteDistance);
                CrowBarHudRenderer.renderVanillaPlayerOverlay(context, ownerUuid, centerX, centerY, size, distance, vanillaColor, vanillaSprite);
            }

            String label = CrowBarHudRenderer.labelText(CrowBarState.nameTagsEnabled ? ownerName : null, distance);
            CrowBarHudRenderer.queueLabel(label, centerX, centerY - size / 2 - 12,
                    MarkerFade.alpha(CrowBarConfigStore.effective(), distance));
        });
    }

    @Unique
    private boolean crowbar$isSelfWaypoint(TrackedWaypoint waypoint, Entity cameraEntity) {
        Either<UUID, String> source = waypoint.id();
        return source != null && source.left()
                .map(uuid -> uuid.equals(cameraEntity.getUUID()))
                .orElse(false);
    }

    /**
     * Blocks to the waypoint's owner, or {@code -1} when no position source knows where they are.
     * Vanilla waypoints only cover nearby entities, but Allium positions (when available) give the
     * true distance beyond render range.
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
}
