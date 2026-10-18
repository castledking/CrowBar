package codes.castled.crowbar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowBarConfigStoreTest {
    @TempDir
    Path dir;

    @Test
    void missingFileGivesTheOriginalLook() {
        CrowBarConfig config = CrowBarConfigStore.read(dir.resolve("crowbar.json"));

        assertTrue(config.nameTags);
        assertTrue(config.skins);
        assertFalse(config.showDistance);
        assertFalse(config.labelBackground);
        assertFalse(config.raiseHud);
        assertTrue(config.dimUnfocusedLabels);
        assertEquals(DotColorMode.TEAM, config.dotColorMode);
        assertEquals(KeyMode.TOGGLE, config.nameTagsKey);
    }

    @Test
    void roundTripsEverySavedField() {
        Path file = dir.resolve("crowbar.json");
        CrowBarConfig config = new CrowBarConfig();
        config.nameTags = false;
        config.showDistanceKey = KeyMode.HOLD;
        config.labelBackground = true;
        config.dotColorMode = DotColorMode.CUSTOM;
        config.customDotColor = "#FF5555";
        config.selfDotColor = "#55FFFF";

        CrowBarConfigStore.write(file, config);
        CrowBarConfig loaded = CrowBarConfigStore.read(file);

        assertFalse(loaded.nameTags);
        assertEquals(KeyMode.HOLD, loaded.showDistanceKey);
        assertTrue(loaded.labelBackground);
        assertEquals(DotColorMode.CUSTOM, loaded.dotColorMode);
        assertEquals("#FF5555", loaded.customDotColor);
        assertEquals("#55FFFF", loaded.selfDotColor);
    }

    @Test
    void viewSelfIsNeverSaved() throws Exception {
        Path file = dir.resolve("crowbar.json");
        CrowBarConfig config = new CrowBarConfig();
        config.viewSelf = true;

        CrowBarConfigStore.write(file, config);

        assertFalse(Files.readString(file).contains("\"viewSelf\""));
        assertFalse(CrowBarConfigStore.read(file).viewSelf);
    }

    @Test
    void repairsInvalidHandEditedValues() throws Exception {
        Path file = dir.resolve("crowbar.json");
        Files.writeString(file, """
                {
                  "nameTagsKey": "SOMETIMES",
                  "dotColorMode": "RAINBOW",
                  "customDotColor": "red",
                  "selfDotColor": "#12",
                  "fadeStart": 500,
                  "fadeEnd": 100,
                  "fadeEndOpacity": 4.0
                }
                """);

        CrowBarConfig config = CrowBarConfigStore.read(file);

        assertEquals(KeyMode.TOGGLE, config.nameTagsKey);
        assertEquals(DotColorMode.TEAM, config.dotColorMode);
        assertEquals("#FFFFFF", config.customDotColor);
        assertEquals(CrowBarConfig.AUTO_COLOR, config.selfDotColor);
        assertTrue(config.fadeEnd > config.fadeStart);
        assertEquals(0.3f, config.fadeEndOpacity);
    }

    @Test
    void unreadableFileFallsBackToDefaultsAndIsKept() throws Exception {
        Path file = dir.resolve("crowbar.json");
        Files.writeString(file, "{ not json");

        CrowBarConfig config = CrowBarConfigStore.read(file);

        assertTrue(config.nameTags);
        assertTrue(Files.exists(dir.resolve("crowbar.json.broken")));
    }
}
