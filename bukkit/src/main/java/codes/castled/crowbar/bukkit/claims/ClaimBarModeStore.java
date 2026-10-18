package codes.castled.crowbar.bukkit.claims;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Each player's vanilla claim marker mode, saved to {@code claim-modes.yml}. Ported from
 * GPExpansion. A missing or unknown value falls back to {@link ClaimBarMode#TRUSTED}.
 */
public final class ClaimBarModeStore {
    private final File file;
    private final Logger logger;
    private final Map<UUID, ClaimBarMode> modes = new ConcurrentHashMap<>();

    public ClaimBarModeStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    private void load() {
        modes.clear();
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            try {
                ClaimBarMode mode = ClaimBarMode.parse(data.getString(key));
                if (mode != null) modes.put(UUID.fromString(key), mode);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public ClaimBarMode get(UUID player) {
        return modes.getOrDefault(player, ClaimBarMode.TRUSTED);
    }

    public void set(UUID player, ClaimBarMode mode) {
        modes.put(player, mode);
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, ClaimBarMode> entry : modes.entrySet()) {
            data.set(entry.getKey().toString(), entry.getValue().name().toLowerCase(Locale.ROOT));
        }
        try {
            data.save(file);
        } catch (IOException e) {
            logger.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
