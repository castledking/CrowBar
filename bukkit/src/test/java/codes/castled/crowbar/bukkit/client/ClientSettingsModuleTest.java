package codes.castled.crowbar.bukkit.client;

import codes.castled.crowbar.CrowBarConfig;
import codes.castled.crowbar.DotColorMode;
import codes.castled.crowbar.ServerSettings;
import codes.castled.crowbar.bukkit.PluginSettings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSettingsModuleTest {
    @Test
    void theClientReadsWhatThePluginSends() {
        PluginSettings.Appearance appearance = new PluginSettings.Appearance(Map.of(
                "labelBackground", true,
                "fadeEnd", 600,
                "fadeEndOpacity", 0.5,
                "dotColorMode", "custom",
                "customDotColor", "#55FFFF"));

        String json = new String(ClientSettingsModule.payload(appearance, true), StandardCharsets.UTF_8);
        ServerSettings server = ServerSettings.parse(json);
        CrowBarConfig effective = ServerSettings.effective(new CrowBarConfig(), server);

        assertTrue(server.acceptsPlayerColor());
        assertTrue(effective.labelBackground);
        assertEquals(600, effective.fadeEnd);
        assertEquals(0.5f, effective.fadeEndOpacity);
        assertEquals(DotColorMode.CUSTOM, effective.dotColorMode);
        assertEquals("#55FFFF", effective.customDotColor);
        assertFalse(server.overrides("raiseHud"));
    }

    @Test
    void readsColourChoices() {
        assertEquals(0x55FFFF, ClientSettingsModule.parseColor(bytes("{\"color\":\"#55FFFF\"}")).rgb());
        assertNull(ClientSettingsModule.parseColor(bytes("{\"color\":\"reset\"}")).rgb());
        assertNull(ClientSettingsModule.parseColor(bytes("{\"color\":\"blue\"}")));
        assertNull(ClientSettingsModule.parseColor(bytes("{\"colour\":\"#55FFFF\"}")));
        assertNull(ClientSettingsModule.parseColor(bytes("#55FFFF")));
        assertNull(ClientSettingsModule.parseColor(bytes("{\"color\":\"#55FFFF\",\"pad\":\"" + "x".repeat(300) + "\"}")));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
