package codes.castled.crowbar;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * CrowBar's settings, opened from Mod Menu or the settings key.
 *
 * <p>The options themselves live in {@link ConfigOptions}; this screen only lays them out as
 * vanilla buttons in two columns under a row of tabs.
 */
public final class CrowBarConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMN_GAP = 10;
    private static final int ROW_SPACING = 24;
    private static final int TAB_WIDTH = 100;
    private static final int TAB_GAP = 4;

    /** Reopening the screen returns to the tab you were last on. */
    private static ConfigOptions.Tab lastTab = ConfigOptions.Tab.GENERAL;

    private final Screen parent;

    public CrowBarConfigScreen(Screen parent) {
        super(Text.translatable("crowbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ConfigOptions.Tab[] tabs = ConfigOptions.Tab.values();
        int tabsX = (width - tabs.length * TAB_WIDTH - (tabs.length - 1) * TAB_GAP) / 2;
        for (int i = 0; i < tabs.length; i++) {
            ConfigOptions.Tab tab = tabs[i];
            ButtonWidget button = ButtonWidget.builder(Text.translatable(tab.translationKey), b -> {
                        lastTab = tab;
                        clearAndInit();
                    })
                    .dimensions(tabsX + i * (TAB_WIDTH + TAB_GAP), 28, TAB_WIDTH, BUTTON_HEIGHT)
                    .build();
            button.active = tab != lastTab;
            addDrawableChild(button);
        }

        CrowBarConfig config = CrowBarConfigStore.config();
        List<ConfigOptions.Option> options = ConfigOptions.options(lastTab);
        int left = width / 2 - BUTTON_WIDTH - COLUMN_GAP / 2;
        int top = 60;
        for (int i = 0; i < options.size(); i++) {
            ConfigOptions.Option option = options.get(i);
            int x = left + (i % 2) * (BUTTON_WIDTH + COLUMN_GAP);
            int y = top + (i / 2) * ROW_SPACING;
            ButtonWidget button = ButtonWidget.builder(message(option, config), b -> {
                        option.cycle(config);
                        CrowBarConfigStore.save();
                        CrowBarState.syncFeatures();
                        // One option can unlock another, e.g. Dot colors and Single color.
                        clearAndInit();
                    })
                    .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.translatable(option.tooltipKey())))
                    .build();
            button.active = option.enabled(config);
            addDrawableChild(button);
        }

        if (lastTab == ConfigOptions.Tab.CONTROLS && client != null) {
            int y = top + ((options.size() + 1) / 2) * ROW_SPACING;
            addDrawableChild(ButtonWidget.builder(Text.translatable("crowbar.config.keybinds"),
                            b -> client.setScreen(new KeybindsScreen(this, client.options)))
                    .dimensions(left, y, BUTTON_WIDTH * 2 + COLUMN_GAP, BUTTON_HEIGHT)
                    .build());
        }

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(width / 2 - 100, height - 28, 200, BUTTON_HEIGHT)
                .build());
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
    }

    private static Text message(ConfigOptions.Option option, CrowBarConfig config) {
        ConfigOptions.Value value = option.value(config);
        MutableText valueText = value.translationKey() != null
                ? Text.translatable(value.translationKey())
                : Text.literal(value.literal());
        if (value.color() >= 0) {
            valueText = valueText.styled(style -> style.withColor(value.color()));
        }
        return Text.translatable(option.labelKey()).append(Text.literal(": ")).append(valueText);
    }
}
