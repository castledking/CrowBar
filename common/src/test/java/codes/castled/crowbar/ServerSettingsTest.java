package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {
    @Test
    void appliesOnlyWhatTheServerChose() {
        ServerSettings server = ServerSettings.parse("{\"appearance\":{\"labelBackground\":true,"
                + "\"dotColorMode\":\"custom\",\"customDotColor\":\"55ffff\",\"fadeEndOpacity\":0.5}}");
        CrowBarConfig own = new CrowBarConfig();
        own.raiseHud = true;

        CrowBarConfig effective = ServerSettings.effective(own, server);

        assertTrue(effective.labelBackground);
        assertEquals(DotColorMode.CUSTOM, effective.dotColorMode);
        assertEquals("#55FFFF", effective.customDotColor);
        assertEquals(0.5f, effective.fadeEndOpacity);
        assertTrue(effective.raiseHud, "settings the server left out stay the player's");
        assertFalse(own.labelBackground, "the player's own settings are never touched");
        assertNotSame(own, effective);
    }

    @Test
    void playersCanIgnoreTheServer() {
        ServerSettings server = ServerSettings.parse("{\"appearance\":{\"labelBackground\":true}}");
        CrowBarConfig own = new CrowBarConfig();
        own.acceptServerAppearance = false;

        assertSame(own, ServerSettings.effective(own, server));
    }

    @Test
    void nothingToApplyReturnsTheSameObject() {
        CrowBarConfig own = new CrowBarConfig();
        assertSame(own, ServerSettings.effective(own, null));
        assertSame(own, ServerSettings.effective(own, ServerSettings.parse("{\"playerColor\":true}")));
    }

    @Test
    void unknownKeysAndBadValuesAreIgnored() {
        ServerSettings server = ServerSettings.parse("{\"appearance\":{\"nameTags\":false,\"labelBackground\":\"yes\","
                + "\"dotColorMode\":\"rainbow\",\"customDotColor\":\"#12\",\"fadeEndOpacity\":2,\"raiseHud\":true}}");

        assertFalse(server.overrides("nameTags"), "feature switches are not the server's to choose");
        assertFalse(server.overrides("labelBackground"));
        assertFalse(server.overrides("dotColorMode"));
        assertFalse(server.overrides("customDotColor"));
        assertFalse(server.overrides("fadeEndOpacity"));
        assertTrue(server.overrides("raiseHud"));
    }

    @Test
    void halfAFadeRangeStillValidates() {
        ServerSettings server = ServerSettings.parse("{\"appearance\":{\"fadeEnd\":50}}");
        CrowBarConfig effective = ServerSettings.effective(new CrowBarConfig(), server);

        assertTrue(effective.fadeEnd > effective.fadeStart);
    }

    @Test
    void playerColorFlag() {
        assertTrue(ServerSettings.parse("{\"playerColor\":true}").acceptsPlayerColor());
        assertFalse(ServerSettings.parse("{\"playerColor\":\"true\"}").acceptsPlayerColor());
        assertFalse(ServerSettings.parse("{}").acceptsPlayerColor());
        assertNull(ServerSettings.parse("nope"));
    }

    @Test
    void everyAppearanceFieldExistsOnTheConfig() throws NoSuchFieldException {
        for (String field : ServerSettings.appearanceFields()) {
            CrowBarConfig.class.getField(field);
        }
    }
}
