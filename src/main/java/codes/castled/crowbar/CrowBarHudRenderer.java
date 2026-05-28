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
import net.minecraft.world.World;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.client.gl.RenderPipelines;

import java.util.UUID;

public final class CrowBarHudRenderer {
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");
    private static final Identifier LOCATOR_BAR_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");

    private CrowBarHudRenderer() {
    }

    public static void renderAlliumRestoredPlayers(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Guard conditions
        if (client.player == null || client.world == null) return;
        if (!CrowBarState.forceShowAll) return;
        if (CrowBarState.alliumPlayerData.isEmpty()) return;
        
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) return;
        
        World world = cameraEntity.getEntityWorld();
        Camera camera = client.gameRenderer.getCamera();
        EntityTickProgress tickProgress = entity ->
                tickCounter.getTickProgress(!world.getTickManager().shouldSkipTick(entity));
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int locatorBarY = screenHeight - 29;
        
        // Count hidden players to determine if we should draw the background
        int hiddenPlayerCount = 0;
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            UUID uuid = alliumData.uuid;
            if (uuid.equals(cameraEntity.getUuid())) continue;
            PlayerEntity loadedPlayer = client.world.getPlayerByUuid(uuid);
            if (loadedPlayer == null || loadedPlayer.isInvisible() || loadedPlayer.isSneaking() || alliumData.wearingPumpkin) {
                hiddenPlayerCount++;
            }
        }
        
        // Only draw locator bar background if there are hidden players (vanilla would be empty)
        if (hiddenPlayerCount > 0) {
            int barX = (screenWidth - 182) / 2;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, barX, locatorBarY, 182, 5);
        }
        
        // Render Allium-restored players
        // Only render players that are hidden (sneaking/invisible/pumpkin)
        // This prevents duplicate rendering with vanilla
        for (AlliumPlayerData alliumData : CrowBarState.alliumPlayerData.values()) {
            if (alliumData.isExpired()) continue;
            
            UUID uuid = alliumData.uuid;
            if (uuid.equals(cameraEntity.getUuid())) continue;
            
            // Only render via Allium if player is hidden (sneaking/invisible/pumpkin)
            // This is the core purpose of the Allium integration
            PlayerEntity loadedPlayer = client.world.getPlayerByUuid(uuid);
            if (loadedPlayer != null && !loadedPlayer.isInvisible() && !loadedPlayer.isSneaking() && !alliumData.wearingPumpkin) {
                // Player is visible via vanilla, skip to avoid duplicate rendering
                continue;
            }
            
            // Calculate position
            double deltaX = alliumData.x - cameraEntity.getX();
            double deltaZ = alliumData.z - cameraEntity.getZ();
            double yaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0;
            yaw = (yaw + 360.0) % 360.0;
            
            // Convert to relative yaw (-60 to 60 range)
            double relativeYaw = yaw - camera.getYaw();
            while (relativeYaw < -180) relativeYaw += 360;
            while (relativeYaw > 180) relativeYaw -= 360;
            
            if (relativeYaw <= -61.0D || relativeYaw > 60.0D) continue;
            
            int baseX = MathHelper.ceil((screenWidth - 9.0F) / 2.0F);
            int markerX = baseX + MathHelper.floor(relativeYaw * 173.0D / 2.0D / 60.0D);
            int markerY = locatorBarY - 2;
            
            double distance = Math.sqrt(deltaX * deltaX + (alliumData.y - cameraEntity.getY()) * (alliumData.y - cameraEntity.getY()) + deltaZ * deltaZ);
            
            // Calculate scaled size
            int sizeHundredths = calculateSizeFromDistance((float) distance, 9);
            int size = sizeHundredths / 100;
            int dotCenterX = markerX + 4;
            int dotCenterY = markerY + 4;
            
            // Always draw tinted locator dot
            int teamColor = getTeamColor(uuid);
            drawTintedLocatorDot(context, dotCenterX, dotCenterY, size, teamColor);
            
            // Optionally draw skin on top
            if (CrowBarState.skinsEnabled) {
                SkinTextures skin = getSkin(uuid);
                if (skin != null) {
                    PlayerSkinDrawer.draw(context, skin, dotCenterX - size / 2, dotCenterY - size / 2, size);
                }
            }
            
            // Draw distance text anchored to dot
            if (CrowBarState.showDistance && distance > 0) {
                String distanceText = String.format("%.0fm", distance);
                int textX = dotCenterX - textRenderer.getWidth(distanceText) / 2;
                int textY = dotCenterY + size / 2 + 2;
                context.drawTextWithShadow(textRenderer, distanceText, textX, textY, 0xFFFFFFFF);
            }
            
            // Draw name tag if enabled
            if (CrowBarState.nameTagsEnabled) {
                String ownerName = getName(uuid);
                int textWidth = textRenderer.getWidth(ownerName);
                int textX = dotCenterX - textWidth / 2;
                int textY = dotCenterY - 10;
                context.drawTextWithShadow(textRenderer, ownerName, textX, textY, 0xFFFFFFFF);
            }
        }
    }

    private static void drawTintedLocatorDot(DrawContext context, int centerX, int centerY, int size, int color) {
        int halfSize = size / 2;
        int x = centerX - halfSize;
        int y = centerY - halfSize;
        
        // For now, use CPU draw with team color and black outline
        // TODO: Use sprite tinting when RenderSystem API is available
        context.fill(x, y, x + size, y + size, color);
        
        // Black outline (1 pixel)
        context.fill(x, y, x + size, y + 1, 0xFF000000); // top
        context.fill(x, y + size - 1, x + size, y + size, 0xFF000000); // bottom
        context.fill(x, y, x + 1, y + size, 0xFF000000); // left
        context.fill(x + size - 1, y, x + size, y + size, 0xFF000000); // right
    }

    private static int getTeamColor(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Try to get from Allium data first (includes team color for hidden players)
        AlliumPlayerData alliumData = CrowBarState.alliumPlayerData.get(uuid);
        if (alliumData != null && !alliumData.isExpired()) {
            // Clamp RGB to 24 bits and convert to ARGB
            int rgb = alliumData.teamColor & 0xFFFFFF;
            return 0xFF000000 | rgb;
        }
        
        // Try to get loaded player entity next
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                return getTeamColor(player);
            }
        }
        
        // Fallback: return white default
        return 0xFFFFFFFF;
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
        int teamColor = getTeamColor(client.player.getUuid());
        drawTintedLocatorDot(context, iconCenterX, iconCenterY, iconSize, teamColor);
        
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
