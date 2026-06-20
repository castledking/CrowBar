package codes.castled.crowbar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gl.RenderPipelines;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CrowBarHudRenderer {
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");
    private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.of("crowbar", "hud/locator_bar_background");
    private static final int LOCATOR_DOT_SIZE = 9;
    private static final int LOCATOR_ARROW_WIDTH = 7;
    private static final int LOCATOR_ARROW_HEIGHT = 5;
    private static final double LOCATOR_ARROW_VERTICAL_VIEW_HALF_ANGLE = 35.0D;
    private static final double LOCATOR_VIEW_MIN_YAW = -61.0D;
    private static final double LOCATOR_VIEW_MAX_YAW = 60.0D;
    private static final Identifier LOCATOR_ARROW_UP = Identifier.of("crowbar", "hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_ARROW_DOWN = Identifier.of("crowbar", "hud/locator_bar_arrow_down");
    private static final Identifier[] LOCATOR_DOT_SPRITES = {
            Identifier.of("crowbar", "hud/locator_bar_dot/default_0"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_1"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_2"),
            Identifier.of("crowbar", "hud/locator_bar_dot/default_3")
    };
    
    // Golden angle (≈137.508°) — matches Allium server's fallback color generator.
    // Ensures a player gets the same hue from both server and client fallback paths.
    private static final float GOLDEN_ANGLE = 137.508f;

    private CrowBarHudRenderer() {
    }

    public static void renderAlliumRestoredPlayers(DrawContext context, RenderTickCounter tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;
        if (CrowBarState.isExternalRenderSuppressed()) return;
        if (!CrowBarState.isIntegratedServer && !CrowBarState.alliumDataReceived) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;

        if (!CrowBarState.hasRenderableAlliumEntries(cameraEntity.getUuid())) return;

        Camera camera = client.gameRenderer.getCamera();
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int locatorBarY = screenHeight - 29;
        int centerX = screenWidth / 2;

        // First pass: collect all renderable entries with computed positions
        List<EntryRenderData> entries = new ArrayList<>();
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            UUID uuid = alliumData.uuid;
            if (uuid.equals(cameraEntity.getUuid())) continue;

            double deltaX = alliumData.x - cameraEntity.getX();
            double deltaZ = alliumData.z - cameraEntity.getZ();
            double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
            yaw = (yaw + 360.0) % 360.0;

            double relativeYaw = yaw - camera.getYaw();
            while (relativeYaw < -180) relativeYaw += 360;
            while (relativeYaw > 180) relativeYaw -= 360;

            double distance = Math.sqrt(deltaX * deltaX + (alliumData.y - cameraEntity.getY()) * (alliumData.y - cameraEntity.getY()) + deltaZ * deltaZ);
            boolean markerVisible = isWithinLocatorView(relativeYaw);
            if (!markerVisible) continue;

            Identifier arrowSprite = getArrowSpriteForPosition(camera, alliumData.x, alliumData.y, alliumData.z);

            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            int sizeHundredths = calculateSizeFromDistance((float) distance, 9);
            int size = sizeHundredths / 100;
            int dotCenterX = markerX + 4;
            int dotCenterY = markerY + 4;
            Identifier dotSprite = getDotSpriteForDistance(distance);

            entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance, dotSprite, arrowSprite, markerVisible));
        }

        // Draw background (always, even if no entries are in view)
        if (!CrowBarState.isXpBarVisible()) {
            int barX = (screenWidth - 182) / 2;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
        }

        if (entries.isEmpty()) return;
        if (CrowBarState.isXpBarVisible()) return;

        // Find the entry closest to screen center
        EntryRenderData closest = entries.stream()
                .filter(EntryRenderData::markerVisible)
                .min(Comparator.comparingInt(e -> Math.abs(e.markerX - centerX)))
                .orElse(null);

        // Draw non-closest entries first with faded text
        for (EntryRenderData entry : entries) {
            if (entry == closest) continue;
            renderAlliumEntry(context, textRenderer, entry, false);
        }

        // Draw closest entry last with full opacity text
        if (closest != null) {
            renderAlliumEntry(context, textRenderer, closest, true);
        }
    }

    private static void renderAlliumEntry(DrawContext context, TextRenderer textRenderer, EntryRenderData entry, boolean isClosest) {
        if (entry.markerVisible) {
            int dotColor = getDotColor(entry.uuid);
            drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.dotSprite, dotColor);
        }
        if (entry.arrowSprite != null) {
            drawVerticalArrow(context, entry.dotCenterX, entry.dotCenterY, entry.arrowSprite);
        }

        if (entry.markerVisible && CrowBarState.skinsEnabled) {
            SkinTextures skin = getSkin(entry.uuid);
            if (skin != null) {
                PlayerSkinDrawer.draw(context, skin, entry.dotCenterX - entry.size / 2, entry.dotCenterY - entry.size / 2, entry.size);
            }
        }

        if (entry.markerVisible && (CrowBarState.nameTagsEnabled || CrowBarState.showDistance)) {
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

            int textColor = isClosest ? 0xFFFFFFFF : 0x70FFFFFF;
            int textWidth = textRenderer.getWidth(text);
            int textX = entry.dotCenterX - textWidth / 2;
            int textY = entry.dotCenterY - entry.size / 2 - 12;
            if (LOCATOR_ARROW_UP.equals(entry.arrowSprite)) {
                textY -= LOCATOR_ARROW_HEIGHT + 1;
            }
            context.drawTextWithShadow(textRenderer, text, textX, textY, textColor);
        }
    }

    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance, Identifier dotSprite, Identifier arrowSprite, boolean markerVisible) {
    }

    private static void drawTintedLocatorDot(DrawContext context, int centerX, int centerY, Identifier sprite, int color) {
        int halfSize = LOCATOR_DOT_SIZE / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;
        
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_DOT_SIZE, LOCATOR_DOT_SIZE, color);
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
        Vec3d cameraPosition = camera.getCameraPos();
        double deltaX = targetX - cameraPosition.x;
        double deltaZ = targetZ - cameraPosition.z;
        double targetCenterY = targetY + 1.0D;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(targetCenterY - cameraPosition.y, horizontalDistance));
        return targetPitch - camera.getPitch();
    }

    private static void drawVerticalArrow(DrawContext context, int centerX, int centerY, Identifier sprite) {
        int x = centerX - LOCATOR_ARROW_WIDTH / 2;
        int y = LOCATOR_ARROW_DOWN.equals(sprite)
                ? centerY + LOCATOR_DOT_SIZE / 2 + 1
                : centerY - LOCATOR_DOT_SIZE / 2 - LOCATOR_ARROW_HEIGHT;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, LOCATOR_ARROW_WIDTH, LOCATOR_ARROW_HEIGHT, 0xFFFFFFFF);
    }

    private static int getDotColor(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Priority 1: Allium-provided team color (includes fallback logic on server side)
        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            int rgb = alliumData.teamColor & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            return argb;
        }
        
        // Priority 2: Client-side loaded PlayerEntity team color
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                int teamColor = getTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    return teamColor;
                }
            }
        }
        
        // Priority 3: Mojang-ish generated fallback color
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

    private static int getTeamColor(PlayerEntity player) {
        Team team = player.getScoreboardTeam();
        
        if (team == null) {
            return 0xFFFFFFFF;
        }

        Formatting color = team.getColor();

        if (color == null) {
            return 0xFFFFFFFF;
        }

        Integer colorValue = color.getColorValue();
        if (colorValue == null) {
            return 0xFFFFFFFF;
        }

        return 0xFF000000 | colorValue;
    }

    private static int calculateSizeFromDistance(float distance, int baseSize) {
        // Shared helper for scaling locator dot/skin size based on distance
        // Uses vanilla waypoint style behavior (near/far distance interpolation)
        // Returns size in hundredths (multiply by 0.01f to get pixels)
        // Note: WaypointStyle API not available in 1.21.11, using fallback constants
        if (distance <= 0) return baseSize * 100;
        
        float nearDistance = 128.0F;
        float farDistance = 256.0F;
        
        float progress = 1.0F - MathHelper.clamp(
                (distance - nearDistance) /
                (farDistance - nearDistance),
                0.0F,
                1.0F
        );

        return (int) MathHelper.lerp(
                progress,
                500,
                baseSize * 100
        );
    }

    private static String getName(UUID uuid) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = networkHandler == null ? null : networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            return entry.getProfile().name();
        }
        if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.getUuid().equals(uuid)) {
            return MinecraftClient.getInstance().player.getName().getString();
        }
        return uuid.toString();
    }

    public static void drawSelfView(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        int barX = (context.getScaledWindowWidth() - 182) / 2;
        int barY = context.getScaledWindowHeight() - 29;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BACKGROUND, barX, barY, 182, 5);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, EXPERIENCE_PROGRESS, 182, 5, 0, 0, barX, barY, 182, 5);

        int iconSize = 9;
        int iconCenterX = context.getScaledWindowWidth() / 2;
        int iconCenterY = barY + 2;
        
        // Draw tinted dot for self
        int dotColor = getDotColor(client.player.getUuid());
        drawTintedLocatorDot(context, iconCenterX, iconCenterY, LOCATOR_DOT_SPRITES[0], dotColor);
        
        // Draw skin on top if enabled
        if (CrowBarState.skinsEnabled) {
            SkinTextures skin = getSkin(client.player.getUuid());
            if (skin != null) {
                PlayerSkinDrawer.draw(context, skin, iconCenterX - iconSize / 2, iconCenterY - iconSize / 2, iconSize);
            }
        }

        if (CrowBarState.nameTagsEnabled) {
            String name = client.player.getGameProfile().name();
            int textX = iconCenterX - client.textRenderer.getWidth(name) / 2;
            context.drawTextWithShadow(client.textRenderer, name, textX, barY - 10, 0xFFFFFFFF);
        }
    }

    private static SkinTextures getSkin(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        PlayerListEntry entry = networkHandler == null ? null : networkHandler.getPlayerListEntry(uuid);
        if (entry != null) {
            return entry.getSkinTextures();
        }
        if (client.player != null && client.player.getUuid().equals(uuid)) {
            return client.player.getSkin();
        }
        return null;
    }
}
