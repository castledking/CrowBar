package codes.castled.crowbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

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
        super(Component.translatable("crowbar.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ConfigOptions.Tab[] tabs = ConfigOptions.Tab.values();
        int tabsX = (width - tabs.length * TAB_WIDTH - (tabs.length - 1) * TAB_GAP) / 2;
        for (int i = 0; i < tabs.length; i++) {
            ConfigOptions.Tab tab = tabs[i];
            Button button = Button.builder(Component.translatable(tab.translationKey), b -> {
                        lastTab = tab;
                        rebuildWidgets();
                    })
                    .bounds(tabsX + i * (TAB_WIDTH + TAB_GAP), 28, TAB_WIDTH, BUTTON_HEIGHT)
                    .build();
            button.active = tab != lastTab;
            addRenderableWidget(button);
        }

        CrowBarConfig config = CrowBarConfigStore.config();
        List<ConfigOptions.Option> options = ConfigOptions.options(lastTab);
        int left = width / 2 - BUTTON_WIDTH - COLUMN_GAP / 2;
        int top = 60;
        for (int i = 0; i < options.size(); i++) {
            ConfigOptions.Option option = options.get(i);
            int x = left + (i % 2) * (BUTTON_WIDTH + COLUMN_GAP);
            int y = top + (i / 2) * ROW_SPACING;
            Button button = Button.builder(message(option, config), b -> {
                        option.cycle(config);
                        CrowBarConfigStore.save();
                        CrowBarState.syncFeatures();
                        // One option can unlock another, e.g. Dot colors and Single color.
                        rebuildWidgets();
                    })
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable(option.tooltipKey())))
                    .build();
            button.active = option.enabled(config);
            addRenderableWidget(button);
        }

        if (lastTab == ConfigOptions.Tab.CONTROLS && minecraft != null) {
            int y = top + ((options.size() + 1) / 2) * ROW_SPACING;
            addRenderableWidget(Button.builder(Component.translatable("crowbar.config.keybinds"),
                            b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
                    .bounds(left, y, BUTTON_WIDTH * 2 + COLUMN_GAP, BUTTON_HEIGHT)
                    .build());
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        context.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    private static Component message(ConfigOptions.Option option, CrowBarConfig config) {
        ConfigOptions.Value value = option.value(config);
        MutableComponent valueText = value.translationKey() != null
                ? Component.translatable(value.translationKey())
                : Component.literal(value.literal());
        if (value.color() >= 0) {
            valueText = valueText.withStyle(style -> style.withColor(value.color()));
        }
        return Component.translatable(option.labelKey()).append(Component.literal(": ")).append(valueText);
    }
}
