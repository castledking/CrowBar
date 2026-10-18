package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigOptionsTest {
    private static ConfigOptions.Option find(ConfigOptions.Tab tab, String labelKey) {
        return ConfigOptions.options(tab).stream()
                .filter(o -> o.labelKey().equals(labelKey))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void featureButtonLocksWhileItsKeyIsHeld() {
        CrowBarConfig config = new CrowBarConfig();
        ConfigOptions.Option nameTags = find(ConfigOptions.Tab.GENERAL, "crowbar.config.nametags");
        ConfigOptions.Option nameTagsKey = find(ConfigOptions.Tab.CONTROLS, "crowbar.config.key.nametags");

        assertTrue(nameTags.enabled(config));
        assertEquals("crowbar.config.on", nameTags.value(config).translationKey());

        nameTagsKey.cycle(config);

        assertEquals(KeyMode.HOLD, config.nameTagsKey);
        assertFalse(nameTags.enabled(config));
        assertEquals("crowbar.config.while_held", nameTags.value(config).translationKey());
    }

    @Test
    void singleColourIsOnlyEditableInCustomMode() {
        CrowBarConfig config = new CrowBarConfig();
        ConfigOptions.Option mode = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.dot_colors");
        ConfigOptions.Option color = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.custom_color");

        assertFalse(color.enabled(config));
        mode.cycle(config);
        mode.cycle(config);

        assertEquals(DotColorMode.CUSTOM, config.dotColorMode);
        assertTrue(color.enabled(config));
    }

    @Test
    void colourValuesNamePresetsAndShowHexOtherwise() {
        CrowBarConfig config = new CrowBarConfig();
        ConfigOptions.Option color = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.custom_color");

        assertEquals("crowbar.color.white", color.value(config).translationKey());
        color.cycle(config);
        assertEquals("crowbar.color.red", color.value(config).translationKey());
        assertEquals(0xFF5555, color.value(config).color());

        config.customDotColor = "#123456";
        assertEquals("#123456", color.value(config).literal());
        color.cycle(config);
        assertEquals("#FFFFFF", config.customDotColor);
    }

    @Test
    void selfColourCyclesThroughEveryPresetAndBackToAuto() {
        CrowBarConfig config = new CrowBarConfig();
        ConfigOptions.Option self = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.self_color");

        assertEquals("crowbar.config.self_color.auto", self.value(config).translationKey());
        for (int i = 0; i < DotColors.Preset.values().length; i++) {
            self.cycle(config);
            assertEquals(DotColors.Preset.values()[i].translationKey(), self.value(config).translationKey());
        }
        self.cycle(config);

        assertEquals(CrowBarConfig.AUTO_COLOR, config.selfDotColor);
    }

    @Test
    void everyOptionHasALabelAndTooltipKey() {
        for (ConfigOptions.Tab tab : ConfigOptions.Tab.values()) {
            List<ConfigOptions.Option> options = ConfigOptions.options(tab);
            assertFalse(options.isEmpty());
            for (ConfigOptions.Option option : options) {
                assertTrue(option.labelKey().startsWith("crowbar.config."));
                assertTrue(option.tooltipKey().endsWith(".tooltip"));
            }
        }
    }

    @Test
    void optionsTheServerChoosesAreLockedToItsValue() {
        CrowBarConfig config = new CrowBarConfig();
        ConfigOptions.Option background = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.label_background");
        ConfigOptions.Option color = find(ConfigOptions.Tab.APPEARANCE, "crowbar.config.custom_color");
        ServerConnection.onServerSettings("{\"appearance\":{\"labelBackground\":true,\"dotColorMode\":\"team\"}}");
        try {
            assertFalse(background.enabled(config));
            assertEquals("crowbar.config.on", background.value(config).translationKey());
            assertEquals("crowbar.config.server_locked.tooltip", background.tooltipKey());
            background.cycle(config);
            assertFalse(config.labelBackground, "a locked option cannot be changed");

            // Single colour only applies to Custom, which the server has ruled out.
            config.dotColorMode = DotColorMode.CUSTOM;
            assertFalse(color.enabled(config));

            config.acceptServerAppearance = false;
            assertTrue(background.enabled(config));
            assertEquals("crowbar.config.off", background.value(config).translationKey());
            assertTrue(color.enabled(config));
        } finally {
            ServerConnection.reset();
        }
        assertEquals("crowbar.config.label_background.tooltip", background.tooltipKey());
    }

    @Test
    void copyKeepsEveryField() {
        CrowBarConfig config = new CrowBarConfig();
        config.labelBackground = true;
        config.viewSelf = true;
        config.dotColorMode = DotColorMode.UNIQUE;
        config.fadeEnd = 1234;
        config.acceptServerAppearance = false;

        CrowBarConfig copy = config.copy();

        assertTrue(copy.labelBackground);
        assertTrue(copy.viewSelf);
        assertEquals(DotColorMode.UNIQUE, copy.dotColorMode);
        assertEquals(1234, copy.fadeEnd);
        assertFalse(copy.acceptServerAppearance);
    }
}
