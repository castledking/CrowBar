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
    private static final Identifier LOCATOR_DOT_SPRITE = Identifier.of("crowbar", "hud/locator_bar_dot/default_0");
    
    // Mojang-style color palette for fallback colors
    private static final int[] MOJANGISH_DOT_COLORS = {
        0x000000, // black
        0x0000AA, // dark_blue
        0x00AA00, // dark_green
        0x00AAAA, // dark_aqua
        0xAA0000, // dark_red
        0xAA00AA, // dark_purple
        0xFFAA00, // gold
        0xAAAAAA, // gray
        0x555555, // dark_gray
        0x5555FF, // blue
        0x55FF55, // green
        0x55FFFF, // aqua
        0xFF5555, // red
        0xFF55FF, // light_purple
        0xFFFF55, // yellow
        0xFFFFFF  // white
    };

    private CrowBarHudRenderer() {
    }

    public static void renderAlliumRestoredPlayers(DrawContext context, RenderTickCounter tickCounter) {
        if (CrowBarState.viewSelfEnabled) return;

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

            if (relativeYaw <= -61.0D || relativeYaw > 60.0D) continue;

            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;

            double distance = Math.sqrt(deltaX * deltaX + (alliumData.y - cameraEntity.getY()) * (alliumData.y - cameraEntity.getY()) + deltaZ * deltaZ);

            int sizeHundredths = calculateSizeFromDistance((float) distance, 9);
            int size = sizeHundredths / 100;
            int dotCenterX = markerX + 4;
            int dotCenterY = markerY + 4;

            entries.add(new EntryRenderData(uuid, markerX, dotCenterX, dotCenterY, size, distance));
        }

        // Draw background (always, even if no entries are in view)
        int barX = (screenWidth - 182) / 2;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);

        if (entries.isEmpty()) return;

        // Find the entry closest to screen center
        EntryRenderData closest = entries.stream()
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
        int dotColor = getDotColor(entry.uuid);
        drawTintedLocatorDot(context, entry.dotCenterX, entry.dotCenterY, entry.size, dotColor);

        if (CrowBarState.skinsEnabled) {
            SkinTextures skin = getSkin(entry.uuid);
            if (skin != null) {
                PlayerSkinDrawer.draw(context, skin, entry.dotCenterX - entry.size / 2, entry.dotCenterY - entry.size / 2, entry.size);
            }
        }

        if (CrowBarState.nameTagsEnabled || CrowBarState.showDistance) {
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
            context.drawTextWithShadow(textRenderer, text, textX, textY, textColor);
        }
    }

    private record EntryRenderData(UUID uuid, int markerX, int dotCenterX, int dotCenterY, int size, double distance) {
    }

    private static void drawTintedLocatorDot(DrawContext context, int centerX, int centerY, int size, int color) {
        int halfSize = size / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;
        
        // Draw tinted vanilla sprite
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LOCATOR_DOT_SPRITE, x, y, size, size, color);
    }

    private static int getDotColor(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Priority 1: Allium-provided team color (includes fallback logic on server side)
        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            int rgb = alliumData.teamColor & 0xFFFFFF;
            int argb = 0xFF000000 | rgb;
            System.out.println("[CrowBar HUD] Allium color for " + uuid + ": " + Integer.toHexString(argb));
            return argb;
        }
        
        // Priority 2: Client-side loaded PlayerEntity team color
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                int teamColor = getTeamColor(player);
                if ((teamColor & 0xFFFFFF) != 0xFFFFFF) {
                    System.out.println("[CrowBar HUD] Client team color for " + uuid + ": " + Integer.toHexString(teamColor));
                    return teamColor;
                }
            }
        }
        
        // Priority 3: Mojang-ish generated fallback color
        int fallback = getMojangishGeneratedColor(uuid);
        System.out.println("[CrowBar HUD] Fallback color for " + uuid + ": " + Integer.toHexString(fallback));
        return fallback;
    }
    
    private static int getMojangishGeneratedColor(UUID uuid) {
        int hash = uuid.hashCode();
        int index = Math.floorMod(hash, MOJANGISH_DOT_COLORS.length);
        return 0xFF000000 | MOJANGISH_DOT_COLORS[index];
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
        drawTintedLocatorDot(context, iconCenterX, iconCenterY, iconSize, dotColor);
        
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
