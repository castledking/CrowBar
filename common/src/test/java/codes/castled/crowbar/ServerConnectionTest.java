package codes.castled.crowbar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConnectionTest {
    private final List<String> sent = new ArrayList<>();
    private String originalColor;

    @BeforeEach
    void setUp() {
        originalColor = CrowBarConfigStore.config().selfDotColor;
        ServerConnection.setColorSender(payload -> sent.add(new String(payload, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        CrowBarConfigStore.config().selfDotColor = originalColor;
        ServerConnection.reset();
        ServerConnection.setColorSender(payload -> false);
    }

    @Test
    void automaticColourSendsNothingOnJoin() {
        CrowBarConfigStore.config().selfDotColor = CrowBarConfig.AUTO_COLOR;
        ServerConnection.onServerSettings("{\"playerColor\":true}");

        assertTrue(sent.isEmpty(), "a colour chosen with /crowbar color must survive joining");
    }

    @Test
    void chosenColourIsSentOnJoinAndOnChange() {
        CrowBarConfigStore.config().selfDotColor = "#55FFFF";
        ServerConnection.onServerSettings("{\"playerColor\":true}");
        assertEquals(List.of("{\"color\":\"#55FFFF\"}"), sent);

        ServerConnection.onConfigSaved();
        assertEquals(1, sent.size(), "saving an unrelated setting does not resend");

        CrowBarConfigStore.config().selfDotColor = CrowBarConfig.AUTO_COLOR;
        ServerConnection.onConfigSaved();
        assertEquals("{\"color\":\"reset\"}", sent.get(1));
    }

    @Test
    void serversThatDoNotShareColoursHearNothing() {
        CrowBarConfigStore.config().selfDotColor = "#55FFFF";
        ServerConnection.onServerSettings("{\"appearance\":{\"raiseHud\":true}}");
        CrowBarConfigStore.config().selfDotColor = "#FF5555";
        ServerConnection.onConfigSaved();

        assertTrue(sent.isEmpty());
    }

    @Test
    void effectiveSettingsFollowTheServerAndReset() {
        CrowBarConfig own = CrowBarConfigStore.config();
        assertSame(own, CrowBarConfigStore.effective());

        ServerConnection.onServerSettings("{\"appearance\":{\"labelBackground\":" + !own.labelBackground + "}}");
        assertEquals(!own.labelBackground, CrowBarConfigStore.effective().labelBackground);

        ServerConnection.reset();
        assertSame(own, CrowBarConfigStore.effective());
        assertFalse(ServerConnection.settings() != null);
    }
}
