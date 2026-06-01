package codes.castled.crowbar;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class CrowBarConfigScreen extends Screen {
    private final Screen parent;

    public CrowBarConfigScreen(Screen parent) {
        super(Text.translatable("crowbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 180;
        int buttonHeight = 20;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 42;

        addDrawableChild(toggleButton(
                "crowbar.config.nametags",
                CrowBarState.nameTagsEnabled,
                x,
                y,
                buttonWidth,
                buttonHeight,
                button -> {
                    CrowBarState.nameTagsEnabled = !CrowBarState.nameTagsEnabled;
                    button.setMessage(toggleText("crowbar.config.nametags", CrowBarState.nameTagsEnabled));
                }
        ));

        addDrawableChild(toggleButton(
                "crowbar.config.skins",
                CrowBarState.skinsEnabled,
                x,
                y + 26,
                buttonWidth,
                buttonHeight,
                button -> {
                    CrowBarState.skinsEnabled = !CrowBarState.skinsEnabled;
                    button.setMessage(toggleText("crowbar.config.skins", CrowBarState.skinsEnabled));
                }
        ));

        addDrawableChild(toggleButton(
                "crowbar.config.view_self",
                CrowBarState.viewSelfEnabled,
                x,
                y + 52,
                buttonWidth,
                buttonHeight,
                button -> {
                    CrowBarState.viewSelfEnabled = !CrowBarState.viewSelfEnabled;
                    button.setMessage(toggleText("crowbar.config.view_self", CrowBarState.viewSelfEnabled));
                }
        ));

        addDrawableChild(toggleButton(
                "crowbar.config.show_distance",
                CrowBarState.showDistance,
                x,
                y + 78,
                buttonWidth,
                buttonHeight,
                button -> {
                    CrowBarState.showDistance = !CrowBarState.showDistance;
                    button.setMessage(toggleText("crowbar.config.show_distance", CrowBarState.showDistance));
                }
        ));
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 76, 0xFFFFFFFF);
    }

    private static ButtonWidget toggleButton(String labelKey, boolean enabled, int x, int y, int width, int height, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(toggleText(labelKey, enabled), action)
                .dimensions(x, y, width, height)
                .build();
    }

    private static Text toggleText(String labelKey, boolean enabled) {
        return Text.translatable(labelKey)
                .append(Text.literal(": "))
                .append(Text.translatable(enabled ? "crowbar.config.on" : "crowbar.config.off"));
    }
}
