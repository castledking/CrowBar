package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every jar ships its own copy of {@code en_us.json}. This keeps the copies identical and makes
 * sure each one names everything the settings screen can show.
 */
class LanguageFilesTest {
    private static List<Path> languageFiles() throws IOException {
        Path root = Path.of(System.getProperty("crowbar.rootDir", ".."));
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> versions = Files.newDirectoryStream(root, "version*")) {
            for (Path version : versions) {
                try (DirectoryStream<Path> loaders = Files.newDirectoryStream(version, Files::isDirectory)) {
                    for (Path loader : loaders) {
                        Path lang = loader.resolve("src/main/resources/assets/crowbar/lang/en_us.json");
                        if (Files.exists(lang)) files.add(lang);
                    }
                }
            }
        }
        files.sort(null);
        return files;
    }

    private static Set<String> requiredKeys() {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("key.crowbar.open_config");
        keys.add("crowbar.config.title");
        CrowBarConfig config = new CrowBarConfig();
        for (ConfigOptions.Tab tab : ConfigOptions.Tab.values()) {
            keys.add(tab.translationKey);
            for (ConfigOptions.Option option : ConfigOptions.options(tab)) {
                keys.add(option.labelKey());
                keys.add(option.tooltipKey());
            }
        }
        // Walk each option through a full cycle so every value it can display is covered.
        for (ConfigOptions.Tab tab : ConfigOptions.Tab.values()) {
            for (ConfigOptions.Option option : ConfigOptions.options(tab)) {
                for (int i = 0; i < 16; i++) {
                    String valueKey = option.value(config).translationKey();
                    if (valueKey != null) keys.add(valueKey);
                    option.cycle(config);
                }
            }
        }
        for (CrowBarFeature feature : CrowBarFeature.values()) {
            feature.setKeyMode(config, KeyMode.HOLD);
        }
        for (ConfigOptions.Option option : ConfigOptions.options(ConfigOptions.Tab.GENERAL)) {
            String valueKey = option.value(config).translationKey();
            if (valueKey != null) keys.add(valueKey);
        }
        // Tooltips that change on a server that chooses settings and shares chosen colours.
        ServerConnection.onServerSettings("{\"appearance\":{\"labelBackground\":true},\"playerColor\":true}");
        try {
            for (ConfigOptions.Tab tab : ConfigOptions.Tab.values()) {
                for (ConfigOptions.Option option : ConfigOptions.options(tab)) {
                    keys.add(option.tooltipKey());
                }
            }
        } finally {
            ServerConnection.reset();
        }
        return keys;
    }

    @Test
    void everyJarShipsTheSameLanguageFile() throws IOException {
        List<Path> files = languageFiles();
        assertFalse(files.isEmpty(), "no language files found");

        String first = Files.readString(files.get(0));
        for (Path file : files) {
            assertEquals(first, Files.readString(file), file + " differs from " + files.get(0));
        }
    }

    @Test
    void languageFileNamesEverythingTheSettingsScreenShows() throws IOException {
        Set<String> required = requiredKeys();
        for (Path file : languageFiles()) {
            JsonObject lang = new Gson().fromJson(Files.readString(file), JsonObject.class);
            List<String> missing = new ArrayList<>();
            for (String key : required) {
                if (!lang.has(key)) missing.add(key);
            }
            assertTrue(missing.isEmpty(), file + " is missing translations: " + missing);
        }
    }
}
