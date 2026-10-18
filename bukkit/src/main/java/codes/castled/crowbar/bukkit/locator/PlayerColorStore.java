package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.bukkit.PluginSettings;
import codes.castled.crowbar.bukkit.Scheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The colours players chose with {@code /crowbar color} or CrowBar's settings screen, saved to
 * {@code colors.yml}.
 *
 * <p>Changes are written at most once a second, since a player clicking through colours in the
 * settings screen sends one change per click.
 */
public final class PlayerColorStore {
    private static final long SAVE_DELAY_TICKS = 20L;

    private final Plugin plugin;
    private final File file;
    private final Logger logger;
    private final Map<UUID, Integer> colors = new ConcurrentHashMap<>();
    private final AtomicBoolean savePending = new AtomicBoolean();

    public PlayerColorStore(Plugin plugin, File file, Logger logger) {
        this.plugin = plugin;
        this.file = file;
        this.logger = logger;
        load();
    }

    private void load() {
        colors.clear();
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            try {
                Integer rgb = PluginSettings.parseColor(data.getString(key));
                if (rgb != null) colors.put(UUID.fromString(key), rgb);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** @return the chosen RGB, or {@code null} when the player has not chosen one */
    public Integer get(UUID player) {
        return colors.get(player);
    }

    /** Stores a colour, or clears it with {@code null}. */
    public void set(UUID player, Integer rgb) {
        if (rgb == null) {
            colors.remove(player);
        } else {
            colors.put(player, rgb & 0xFFFFFF);
        }
        if (savePending.compareAndSet(false, true)) {
            Scheduler.later(plugin, this::flush, SAVE_DELAY_TICKS);
        }
    }

    /** Writes any change not saved yet. Called on disable, when scheduled saves no longer run. */
    public void flush() {
        if (savePending.compareAndSet(true, false)) {
            save();
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : colors.entrySet()) {
            data.set(entry.getKey().toString(), PluginSettings.formatColor(entry.getValue()));
        }
        try {
            data.save(file);
        } catch (IOException e) {
            logger.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
