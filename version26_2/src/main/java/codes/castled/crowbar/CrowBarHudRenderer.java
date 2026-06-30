package codes.castled.crowbar;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CrowBarHudRenderer {
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");
    private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_background");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_ARROW_WIDTH = 7;
    private static final int LOCATOR_ARROW_HEIGHT = 5;
    private static final double LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE = 35.0D;
    private static final double LOCATOR_VIEW_MIN_YAW = -61.0D;
    private static final double LOCATOR_VIEW_MAX_YAW = 60.0D;
    private static final Identifier LOCATOR_ARROW_UP = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_ARROW_DOWN = Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_arrow_down");
    private static final Identifier[] LOCATOR_DOT_SPRITES = {
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_0"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_1"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_2"),
            Identifier.fromNamespaceAndPath("crowbar", "hud/locator_bar_dot/default_3")
    };

    private static final float GOLDEN_ANGLE = 137.508f;

    private CrowBarHudRenderer() {
    }

    public static void renderLocatorBarPlayers(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        Camera camera = client.gameRenderer.mainCamera();
        Font font = client.font;
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int locatorBarY = screenHeight - 29;
        int centerX = screenWidth / 2;

        List<EntryRenderData> entries = new ArrayList<>();

        boolean hasAlliumSource = false;
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            hasAlliumSource = true;
            UUID uuid = alliumData.uuid;
            addEntryForPosition(cameraEntity, camera, screenWidth, locatorBarY, entries, uuid, alliumData.x, alliumData.y, alliumData.z, true);
        }

        boolean hasIntegratedServerSource = client.getSingleplayerServer() != null;
        if (!hasAlliumSource && hasIntegratedServerSource) {
            for (ServerPlayer player : client.getSingleplayerServer().getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();
                if (uuid.equals(cameraEntity.getUUID())) continue;
                addEntryForPosition(cameraEntity, camera, screenWidth, locatorBarY, entries, uuid, player.getX(), player.getY(), player.getZ(), false);
            }
        }

        if (!hasAlliumSource && !hasIntegratedServerSource) {
            if (!CrowBarState.isVanillaLocatorBarVisible()) return;
            for (Player player : client.level.players()) {
                UUID uuid = player.getUUID();
                if (uuid.equals(cameraEntity.getUUID())) continue;
                if (getPlayerInfo(uuid) == null) continue;
                addEntryForPosition(cameraEntity, camera, screenWidth, locatorBarY, entries, uuid, player.getX(), player.getY(), player.getZ(), false);
            }
        }

        if ((CrowBarState.hasAlliumDataReceived() || CrowBarState.isIntegratedServer)
                && CrowBarState.hasRenderablePlayers(client.player.getUUID())
                && !CrowBarState.isXpBarVisible()) {
            int barX = (screenWidth - 182) / 2;
            context.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
        }

        if (CrowBarState.isXpBarVisible()) return;

        if (entries.isEmpty()) return;

        EntryRenderData closest = entries.stream()
                .filter(EntryRenderData::markerVisible)
                .min(Comparator.comparingInt(e -> Math.abs(e.markerX - centerX)))
                .orElse(null);

        for (EntryRenderData entry : entries) {
            if (entry == closest) continue;
            renderLocatorDot(context, entry);
        }

        if (closest != null) {
            renderLocatorDot(context, closest);
        }

        if (CrowBarState.skinsEnabled) {
            for (EntryRenderData entry : entries) {
                if (!entry.markerVisible) continue;
                if (entry == closest) continue;
                renderLocatorSkin(context, entry);
            }

            if (closest != null) {
                renderLocatorSkin(context, closest);
            }
        }

        if (CrowBarState.nameTagsEnabled || CrowBarState.showDistance) {
            for (EntryRenderData entry : entries) {
                if (!entry.markerVisible) continue;
                if (entry == closest) continue;
                renderLocatorLabel(context, font, entry, false);
            }

            if (closest != null) {
                renderLocatorLabel(context, font, closest, true);
            }
        }
    }

    private static void addEntryForPosition(Entity cameraEntity, Camera camera, int screenWidth, int locatorBarY, List<EntryRenderData> entries, UUID uuid, double x, double y, double z, boolean allowVerticalArrow) {
        if (uuid.equals(cameraEntity.getUUID())) return;

        double deltaX = x - cameraEntity.getX();
        double deltaZ = z - cameraEntity.getZ();
        double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
        yaw = (yaw + 360.0) % 360.0;

        double relativeYaw = yaw - camera.yRot();
        while (relativeYaw < -180) relativeYaw += 360;
        while (relativeYaw > 180) relativeYaw -= 360;

        double distance = Math.sqrt(deltaX * deltaX + (y - cameraEntity.getY()) * (y - cameraEntity.getY()) + deltaZ * deltaZ);
        boolean markerVisible = isWithinLocatorView(relativeYaw);
        if (!markerVisible) return;

        Identifier arrowSprite = allowVerticalArrow
                ? getArrowSpriteForPosition(camera, x, y, z)
                : null;

        int baseX = Mth.ceil((screenWidth - 9.0F) / 2.0F);
        int markerX = baseX + Mth.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
        int markerY = locatorBarY - 2;

        int sizeHundredths = calculateSizeFromDistance((float) distance, 9);
        int size = sizeHundredths / 100;
        int dotCenterX = markerX + 4;
        int dotCenterY = markerY + 4;
        Identifier dotSprite = getDotSpriteForDistance(distance);

        entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance, dotSprite, arrowSprite, markerVisible));
    }

    private static void renderLocatorDot(GuiGraphicsExtractor context, EntryRenderData entry) {
        if (entry.markerVisible) {
            int dotColor = getDotColor(entry.uuid);
            drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite, dotColor);
        }
        if (entry.arrowSprite != null) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite);
        }
    }

    private static void renderLocatorSkin(GuiGraphicsExtractor context, EntryRenderData entry) {
        if (!entry.markerVisible) return;
        renderPlayerFace(context, entry.uuid, entry.dotCenterX - entry.size / 2, entry.dotCenterY - entry.size / 2, entry.size);
    }

    private static void renderLocatorLabel(GuiGraphicsExtractor context, Font font, EntryRenderData entry, boolean isClosest) {
        if (!entry.markerVisible) return;
        String text = "";
        if (CrowBarState.nameTagsEnabled) {
            text = getName(entry.uuid);
        }
        if (CrowBarState.showDistance && entry.distance > 0) {
            String distanceText = String.format("%.0fm", entry.distance);
            if (CrowBarState.nameTagsEnabled) {
                text = text + " " + distanceText;
            } else {
                text = distanceText;
            }
        }

        if (!text.isEmpty()) {
            int textColor = isClosest ? 0xFFFFFFFF : 0x70FFFFFF;
            int textWidth = font.width(text);
            int textX = entry.dotCenterX - textWidth / 2;
            int textY = entry.dotCenterY - entry.size / 2 - 12;
            if (LOCATOR_ARROW_UP.equals(entry.arrowSprite)) {
                textY -= LOCATOR_ARROW_HEIGHT + 1;
            }
            context.text(font, text, textX, textY, textColor);
        }
    }

    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, Identifier dotSprite, Identifier arrowSprite, boolean markerVisible) {
    }

    private static void drawTintedLocatorDot(GuiGraphicsExtractor context, int centerX, int centerY, Identifier sprite, int color) {
        int halfSize = LOCATOR_DOT_SIZE / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, color);
    }

    private static Identifier getDotSpriteForDistance(double distance) {
        if (distance >= 333.0D) return LOCATOR_DOT_SPRITES[3];
        if (distance >= 231.0D) return LOCATOR_DOT_SPRITES[2];
        if (distance >= 129.0D) return LOCATOR_DOT_SPRITES[1];
        return LOCATOR_DOT_SPRITES[0];
    }

    private static boolean isWithinLocatorView(double relativeYaw) {
        return relativeYaw > LOCATOR_VIEW_MIN_YAW && relativeYaw <= LOCATOR_VIEW_MAX_YAW;
    }

    private static Identifier getArrowSpriteForPosition(Camera camera, double targetX, double targetY, double targetZ) {
        double pitchDelta = getTargetPitchDelta(camera, targetX, targetY, targetZ);
        if (Math.abs(pitchDelta) <= LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE) return null;
        return pitchDelta < 0.0D ? LOCATOR_ARROW_UP : LOCATOR_ARROW_DOWN;
    }

    private static double getTargetPitchDelta(Camera camera, double targetX, double targetY, double targetZ) {
        Vec3 cameraPosition = camera.position();
        double deltaX = targetX - cameraPosition.x();
        double deltaZ = targetZ - cameraPosition.z();
        double targetCenterY = targetY + 1.0D;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(targetCenterY - cameraPosition.y(), horizontalDistance));
        return targetPitch - camera.xRot();
    }

    private static void drawVerticalArrow(GuiGraphicsExtractor context, int centerX, int centerY, Identifier sprite) {
        int x = centerX - LOCATOR_ARROW_WIDTH / 2;
        int y = LOCATOR_ARROW_DOWN.equals(sprite)
                ? centerY + LOCATOR_DOT_SIZE / 2 + 1
                : centerY - LOCATOR_DOT_SIZE / 2 - LOCATOR_ARROW_HEIGHT;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_ARROW_WIDTH, LOCATOR_ARROW_HEIGHT, 0xFFFFFFFF);
    }

    private static int getDotColor(UUID uuid) {
        Minecraft client = Minecraft.getInstance();

        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            int rgb = alliumData.teamColor & 0xFFFFFF;
            return 0xFF000000 | rgb;
        }

        if (client.level != null) {
            Player player = client.level.getPlayerInAnyDimension(uuid);
            if (player != null) {
                int teamColor = getTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    return teamColor;
                }
            }
        }

        int fallback = getMojangishGeneratedColor(uuid);
        return fallback;
    }

    private static int getMojangishGeneratedColor(UUID uuid) {
        long hash = uuid.hashCode() & 0xffffffffL;
        float hue = (hash * GOLDEN_ANGLE) % 360f;
        float saturation = 0.75f + ((hash >> 8) & 0xFF) / 255f * 0.20f;
        float brightness = 0.85f + ((hash >> 16) & 0xFF) / 255f * 0.15f;
        int rgb = java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness) & 0xFFFFFF;
        return 0xFF000000 | rgb;
    }

    private static int getTeamColor(Player player) {
        PlayerTeam team = player.getTeam();

        if (team == null) {
            return 0xFFFFFFFF;
        }

        return 0xFF000000 | team.getColor()
                .map(net.minecraft.world.scores.TeamColor::rgb)
                .orElse(0xFFFFFF);
    }

    private static int calculateSizeFromDistance(float distance, int baseSize) {
        if (distance <= 0) return baseSize * 100;

        float nearDistance = 128.0F;
        float farDistance = 256.0F;

        float progress = 1.0F - Mth.clamp(
                (distance - nearDistance) /
                (farDistance - nearDistance),
                0.0F,
                1.0F
        );

        return (int) Mth.lerp(
                progress,
                500,
                baseSize * 100
        );
    }

    private static String getName(UUID uuid) {
        PlayerInfo entry = getPlayerInfo(uuid);
        if (entry != null) {
            return VersionCompat.getGameProfileName(entry.getProfile());
        }
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(uuid)) {
            return Minecraft.getInstance().player.getName().getString();
        }
        return uuid.toString();
    }

    private static void renderPlayerFace(GuiGraphicsExtractor context, UUID uuid, int x, int y, int size) {
        PlayerInfo entry = getPlayerInfo(uuid);
        PlayerSkin skin = entry != null ? entry.getSkin() : getLoadedPlayerSkin(uuid);
        if (skin == null) {
            return;
        }

        boolean showHat = entry == null || entry.showHat();
        boolean upsideDown = isPlayerUpsideDown(uuid);
        Identifier texture = skin.body().texturePath();
        PlayerFaceExtractor.extractRenderState(context, texture, x, y, size, showHat, upsideDown, -1);
    }

    private static PlayerInfo getPlayerInfo(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        return connection == null ? null : connection.getPlayerInfo(uuid);
    }

    private static PlayerSkin getLoadedPlayerSkin(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getUUID().equals(uuid)) {
            return client.player.getSkin();
        }

        Player player = getLoadedPlayer(uuid);
        if (player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin();
        }
        return null;
    }

    private static boolean isPlayerUpsideDown(UUID uuid) {
        Player player = getLoadedPlayer(uuid);
        return player != null && AvatarRenderer.isPlayerUpsideDown(player);
    }

    private static Player getLoadedPlayer(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? null : client.level.getPlayerInAnyDimension(uuid);
    }

    public static void drawSelfView(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int barX = (context.guiWidth() - 182) / 2;
        int barY = context.guiHeight() - 29;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_PROGRESS, 182, 5, 0, 0, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.guiWidth() / 2;
        int iconCenterY = barY + 2;

        int dotColor = getDotColor(client.player.getUUID());
        drawTintedLocatorDot(context, iconCenterX, iconCenterY, LOCATOR_DOT_SPRITES[0], dotColor);

        if (CrowBarState.skinsEnabled) {
            renderPlayerFace(context, client.player.getUUID(), iconCenterX - iconSize / 2, iconCenterY - iconSize / 2, iconSize);
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = VersionCompat.getGameProfileName(client.player.getGameProfile());
            int textX = iconCenterX - client.font.width(name) / 2;
            context.text(client.font, name, textX, barY - 10, 0xFFFFFFFF);
        }
    }
}
