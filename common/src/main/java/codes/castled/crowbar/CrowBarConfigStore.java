package codes.castled.crowbar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and saves {@link CrowBarConfig}. The loader entrypoint calls {@link #init} once at startup. */
public final class CrowBarConfigStore {
    public static final String FILE_NAME = "crowbar.json";

    private static final Logger LOGGER = LoggerFactory.getLogger("CrowBar");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile CrowBarConfig config = new CrowBarConfig();
    /** {@link #config} with the server's appearance choices applied; rebuilt after any change. */
    private static volatile CrowBarConfig effective;
    private static Path file;

    private CrowBarConfigStore() {
    }

    public static void init(Path configDir) {
        file = configDir.resolve(FILE_NAME);
        config = read(file);
        invalidate();
        // Rewrite straight away so options added in a newer release show up in the file.
        write(file, config);
    }

    /** Your own settings, as the settings screen and keys change them. */
    public static CrowBarConfig config() {
        return config;
    }

    /**
     * The settings to draw with: {@link #config()} plus any appearance settings the server chose.
     * Treat the result as read-only; it may be a copy.
     */
    public static CrowBarConfig effective() {
        CrowBarConfig current = effective;
        if (current == null) {
            current = ServerSettings.effective(config, ServerConnection.settings());
            effective = current;
        }
        return current;
    }

    /** Drops the cached {@link #effective()} settings after the server's settings change. */
    static void invalidate() {
        effective = null;
    }

    public static void save() {
        invalidate();
        if (file != null) {
            write(file, config);
        }
        ServerConnection.onConfigSaved();
    }

    static CrowBarConfig read(Path file) {
        if (!Files.exists(file)) {
            return new CrowBarConfig();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CrowBarConfig loaded = GSON.fromJson(reader, CrowBarConfig.class);
            if (loaded == null) {
                return new CrowBarConfig();
            }
            loaded.validate();
            return loaded;
        } catch (IOException | JsonParseException e) {
            // Keep the unreadable file next to the fresh one rather than silently losing it.
            LOGGER.warn("Could not read {}, using defaults", file, e);
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            return new CrowBarConfig();
        }
    }

    static void write(Path file, CrowBarConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save {}", file, e);
        }
    }
}
