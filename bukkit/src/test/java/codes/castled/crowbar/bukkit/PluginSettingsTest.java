package codes.castled.crowbar.bukkit;

import codes.castled.crowbar.bukkit.locator.ColorMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSettingsTest {
    private static Logger capturing(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    @Test
    void bundledConfigParsesWithoutWarnings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = PluginSettingsTest.class.getResourceAsStream("/config.yml")) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> warnings = new ArrayList<>();

        PluginSettings settings = PluginSettings.from(config, capturing(warnings));

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(settings.locator().enabled());
        assertTrue(settings.locator().deferToAllium());
        assertEquals(2, settings.locator().updateIntervalTicks());
        assertTrue(settings.hiding().sneaking());
        assertTrue(settings.hiding().headItems().contains(Material.CARVED_PUMPKIN));
        assertTrue(settings.hiding().headItems().contains(Material.PLAYER_HEAD));
        assertEquals(ColorMode.TEAM, settings.colors().mode());
        assertTrue(settings.precision().sendDistance());
        assertEquals(0, settings.precision().directionSteps());
        assertTrue(settings.claims().enabled());
        assertTrue(settings.claims().vanillaMarkers());
    }

    @Test
    void repairsInvalidValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                player-locator:
                  update-interval-ticks: 0
                  max-distance: -5
                hide-when:
                  head-items: [CARVED_PUMPKIN, NOT_AN_ITEM]
                colors:
                  mode: rainbow
                  constant-color: blue
                  players:
                    Steve: '#55ffff'
                    Alex: nope
                """);
        List<String> warnings = new ArrayList<>();

        PluginSettings settings = PluginSettings.from(config, capturing(warnings));

        assertEquals(1, settings.locator().updateIntervalTicks());
        assertEquals(0, settings.locator().maxDistance());
        assertEquals(1, settings.hiding().headItems().size());
        assertEquals(ColorMode.TEAM, settings.colors().mode());
        assertEquals(0xFFFFFF, settings.colors().constantColor());
        assertEquals(0x55FFFF, settings.colors().players().get("steve"));
        assertFalse(settings.colors().players().containsKey("alex"));
        assertEquals(4, warnings.size(), warnings.toString());
    }

    @Test
    void clientAppearanceLeavesPlayerSettingsAlone() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("client-appearance.label-background", true);
        config.set("client-appearance.raise-hud", "player");
        config.set("client-appearance.skin-frame", false);
        config.set("client-appearance.fade-end", 600);
        config.set("client-appearance.fade-end-opacity", 0.5);
        config.set("client-appearance.dot-colors", "per-player");
        config.set("client-appearance.dot-color", "55ffff");
        List<String> warnings = new ArrayList<>();

        var appearance = PluginSettings.from(config, capturing(warnings)).appearance().settings();

        assertEquals(true, appearance.get("labelBackground"));
        assertFalse(appearance.containsKey("raiseHud"));
        assertEquals(false, appearance.get("skinBorder"));
        assertEquals(600, appearance.get("fadeEnd"));
        assertEquals(0.5, appearance.get("fadeEndOpacity"));
        assertEquals("unique", appearance.get("dotColorMode"));
        assertEquals("#55FFFF", appearance.get("customDotColor"));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void invalidClientAppearanceWarnsAndFallsBackToPlayer() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("client-appearance.label-background", "yes");
        config.set("client-appearance.fade-end-opacity", 3);
        config.set("client-appearance.dot-colors", "rainbow");
        List<String> warnings = new ArrayList<>();

        var appearance = PluginSettings.from(config, capturing(warnings)).appearance().settings();

        assertTrue(appearance.isEmpty());
        assertEquals(3, warnings.size(), warnings.toString());
    }
}
