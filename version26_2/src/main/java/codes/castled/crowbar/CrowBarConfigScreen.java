package codes.castled.crowbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class CrowBarConfigScreen extends Screen {
    private final Screen parent;

    public CrowBarConfigScreen(Screen parent) {
        super(Component.translatable("crowbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 180;
        int buttonHeight = 20;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 42;

        addRenderableWidget(toggleButton(
                "crowbar.config.nametags",
                CrowBarState.nameTagsEnabled,
                x, y,
                buttonWidth, buttonHeight,
                button -> {
                    CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                    button.setMessage(toggleText("crowbar.config.nametags", CrowBarState.nameTagsEnabled));
                }
        ));

        addRenderableWidget(toggleButton(
                "crowbar.config.skins",
                CrowBarState.skinsEnabled,
                x, y + 26,
                buttonWidth, buttonHeight,
                button -> {
                    CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                    button.setMessage(toggleText("crowbar.config.skins", CrowBarState.skinsEnabled));
                }
        ));

        addRenderableWidget(toggleButton(
                "crowbar.config.view_self",
                CrowBarState.viewSelfEnabled,
                x, y + 52,
                buttonWidth, buttonHeight,
                button -> {
                    CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                    button.setMessage(toggleText("crowbar.config.view_self", CrowBarState.viewSelfEnabled));
                }
        ));

        addRenderableWidget(toggleButton(
                "crowbar.config.show_distance",
                CrowBarState.showDistance,
                x, y + 78,
                buttonWidth, buttonHeight,
                button -> {
                    CrowBarState.showDistance = !CrowBarState.showDistance;
                    button.setMessage(toggleText("crowbar.config.show_distance", CrowBarState.showDistance));
                }
        ));
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        context.centeredText(font, title, width / 2, height / 2 - 76, 0xFFFFFFFF);
    }

    private static Button toggleButton(String labelKey, boolean enabled, int x, int y, int width, int height, Button.OnPress action) {
        return Button.builder(toggleText(labelKey, enabled), action)
                .bounds(x, y, width, height)
                .build();
    }

    private static Component toggleText(String labelKey, boolean enabled) {
        return Component.translatable(labelKey)
                .append(Component.literal(": "))
                .append(Component.translatable(enabled ? "crowbar.config.on" : "crowbar.config.off"));
    }
}
