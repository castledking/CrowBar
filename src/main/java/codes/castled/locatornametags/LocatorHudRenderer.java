package codes.castled.locatornametags;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;

import java.util.UUID;

public final class LocatorHudRenderer {
    private static final Identifier EXPERIENCE_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier EXPERIENCE_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");
    private static final Identifier DEFAULT_LOCATOR_DOT = Identifier.ofVanilla("hud/locator_bar_dot/default_3");

    private LocatorHudRenderer() {
    }

    public static void drawSkinOrDot(DrawContext context, UUID uuid, int centerX, int centerY, int size) {
        if (LocatorNameTagsState.skinsEnabled) {
            SkinTextures skin = getSkin(uuid);
            if (skin != null) {
                PlayerSkinDrawer.draw(context, skin, centerX - size / 2, centerY - size / 2, size);
                return;
            }
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, DEFAULT_LOCATOR_DOT, centerX - 4, centerY - 4, 9, 9);
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
        drawSkinOrDot(context, client.player.getUuid(), iconCenterX, iconCenterY, iconSize);

        if (LocatorNameTagsState.nameTagsEnabled) {
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
