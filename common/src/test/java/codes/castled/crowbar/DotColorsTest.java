package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DotColorsTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    void teamModeUsesTheTeamColourWhenThereIsOne() {
        CrowBarConfig config = new CrowBarConfig();

        assertEquals(0xFFFF5555, DotColors.forPlayer(config, STEVE, 0x00FF5555));
        assertEquals(DotColors.generated(STEVE), DotColors.forPlayer(config, STEVE, null));
    }

    @Test
    void uniqueModeIgnoresTeams() {
        CrowBarConfig config = new CrowBarConfig();
        config.dotColorMode = DotColorMode.UNIQUE;

        assertEquals(DotColors.generated(STEVE), DotColors.forPlayer(config, STEVE, 0xFFFF5555));
    }

    @Test
    void customModePaintsEveryoneTheSameColour() {
        CrowBarConfig config = new CrowBarConfig();
        config.dotColorMode = DotColorMode.CUSTOM;
        config.customDotColor = "#55FFFF";

        assertEquals(0xFF55FFFF, DotColors.forPlayer(config, STEVE, 0xFFFF5555));
    }

    @Test
    void selfColourOverridesOnlyWhenChosen() {
        CrowBarConfig config = new CrowBarConfig();

        assertEquals(0xFFFF5555, DotColors.forSelf(config, STEVE, 0xFFFF5555));

        config.selfDotColor = "#FFAA00";
        assertEquals(0xFFFFAA00, DotColors.forSelf(config, STEVE, 0xFFFF5555));
    }

    @Test
    void generatedColourIsOpaqueAndStable() {
        int color = DotColors.generated(STEVE);

        assertEquals(0xFF, color >>> 24);
        assertEquals(color, DotColors.generated(UUID.fromString(STEVE.toString())));
    }

    @Test
    void scalesAlpha() {
        assertEquals(0x80FFFFFF, DotColors.withAlpha(0xFFFFFFFF, 128));
        assertEquals(0x00FFFFFF, DotColors.withAlpha(0xFFFFFFFF, 0));
        assertEquals(0x38FFFFFF, DotColors.withAlpha(0x70FFFFFF, 128));
    }

    @Test
    void parsesHex() {
        assertEquals(0xFFAA00, DotColors.parseHex("#FFAA00"));
        assertEquals(0xFFAA00, DotColors.parseHex("ffaa00"));
        assertNull(DotColors.parseHex("orange"));
        assertNull(DotColors.parseHex("#FFF"));
        assertEquals("#00AAAA", DotColors.toHex(0xFF00AAAA));
    }

    @Test
    void hashColourMatchesVanillasBrightness() {
        int color = DotColors.fromHash("claim-1".hashCode());

        assertEquals(0xFF, color >>> 24);
        int max = Math.max(Math.max((color >> 16) & 0xFF, (color >> 8) & 0xFF), color & 0xFF);
        assertEquals(Math.round(0.9F * 255), max, 1);
    }
}
