package codes.castled.locatornametags.mixin;

import codes.castled.locatornametags.LocatorHudRenderer;
import codes.castled.locatornametags.LocatorNameTagsState;
import com.mojang.datafixers.util.Either;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
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
public abstract class LocatorBarRendererMixin {
    @Unique
    private static final ConcurrentHashMap<UUID, String> ALLIUM$UUID_NAME_CACHE = new ConcurrentHashMap<>();

    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
    private void allium$hideLocatorBarInSelfView(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (LocatorNameTagsState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAddons", at = @At("HEAD"), cancellable = true)
    private void allium$hideLocatorAddonsInSelfView(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (LocatorNameTagsState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Unique
    private String allium$getName(UUID uuid) {
        String cached = ALLIUM$UUID_NAME_CACHE.get(uuid);
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
        ALLIUM$UUID_NAME_CACHE.put(uuid, name);
        return name;
    }

    @Inject(method = "renderAddons", at = @At("TAIL"))
    private void allium$renderLocatorAddons(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
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
            if (allium$isSelfWaypoint(waypoint, cameraEntity)) {
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
                ownerName = source.map(this::allium$getName, name -> name);
            }

            boolean valid = markerX >= 0 && markerX < screenWidth && markerY >= 0 && markerY < screenHeight;
            UUID ownerUuid = source == null ? null : source.left().orElse(null);
            waypoints.add(new WaypointRenderData(markerX, markerY, ownerName, ownerUuid, valid));
        });

        WaypointRenderData closest = waypoints.stream()
                .filter(WaypointRenderData::valid)
                .min(Comparator.comparingInt(data -> Math.abs(data.x() - centerX)))
                .orElse(null);

        for (WaypointRenderData data : waypoints) {
            if (!data.valid() || data == closest) {
                continue;
            }
            allium$drawWaypoint(context, textRenderer, data, 0x70FFFFFF);
        }

        if (closest != null && closest.valid()) {
            allium$drawWaypoint(context, textRenderer, closest, 0xFFFFFFFF);
        }
    }

    @Unique
    private boolean allium$isSelfWaypoint(TrackedWaypoint waypoint, Entity cameraEntity) {
        Either<UUID, String> source = waypoint.getSource();
        return source != null && source.left()
                .map(uuid -> uuid.equals(cameraEntity.getUuid()))
                .orElse(false);
    }

    @Unique
    private void allium$drawWaypoint(DrawContext context, TextRenderer textRenderer, WaypointRenderData data, int color) {
        if (LocatorNameTagsState.skinsEnabled && data.ownerUuid() != null) {
            LocatorHudRenderer.drawSkinOrDot(context, data.ownerUuid(), data.x() + 4, data.y() + 4, 9);
        }

        if (!LocatorNameTagsState.nameTagsEnabled) {
            return;
        }

        int textWidth = textRenderer.getWidth(data.ownerName());
        int textX = data.x() - textWidth / 2;
        int textY = data.y() - 10;
        context.drawTextWithShadow(textRenderer, data.ownerName(), textX, textY, color);
    }

    @Unique
    private record WaypointRenderData(int x, int y, String ownerName, UUID ownerUuid, boolean valid) {
    }
}
