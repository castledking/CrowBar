package codes.castled.crowbar.bukkit.client;

import codes.castled.crowbar.bukkit.PluginSettings;
import codes.castled.crowbar.bukkit.Scheduler;
import codes.castled.crowbar.bukkit.locator.ColorMode;
import codes.castled.crowbar.bukkit.locator.PlayerColorStore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Talks to CrowBar clients about their settings.
 *
 * <ul>
 *   <li>Sends {@code crowbar:server_settings}: the appearance settings from {@code client-appearance},
 *       and whether this server shows other players the dot colour a player picks in CrowBar.
 *   <li>Receives {@code crowbar:player_color}: that colour, stored exactly as {@code /crowbar color}
 *       would store it.
 * </ul>
 */
public final class ClientSettingsModule implements Listener {
    public static final String SETTINGS_CHANNEL = "crowbar:server_settings";
    public static final String COLOR_CHANNEL = "crowbar:player_color";
    private static final int MAX_COLOR_MESSAGE_BYTES = 256;

    private final Plugin plugin;
    private final PluginSettings settings;
    private final PlayerColorStore colors;
    /** Colours only reach anyone while this plugin's own player locator is sending positions. */
    private final boolean locatorActive;

    public ClientSettingsModule(Plugin plugin, PluginSettings settings, PlayerColorStore colors, boolean locatorActive) {
        this.plugin = plugin;
        this.settings = settings;
        this.colors = colors;
        this.locatorActive = locatorActive;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // After a reload, tell everyone already online what changed.
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTo(player);
        }
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sendLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegistered(PlayerRegisterChannelEvent event) {
        // Clients can register channels before they have finished joining, or after.
        if (SETTINGS_CHANNEL.equals(event.getChannel())) {
            sendLater(event.getPlayer());
        }
    }

    private void sendLater(Player player) {
        UUID id = player.getUniqueId();
        Scheduler.later(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) sendTo(online);
        }, 1L);
    }

    /** Whether {@code player} may pick their dot colour in CrowBar and have others see it. */
    public boolean acceptsColorFrom(Player player) {
        return locatorActive && settings.colors().mode() == ColorMode.CUSTOM && player.hasPermission("crowbar.color");
    }

    private void sendTo(Player player) {
        if (!player.isOnline() || !player.getListeningPluginChannels().contains(SETTINGS_CHANNEL)) return;
        try {
            player.sendPluginMessage(plugin, SETTINGS_CHANNEL, payload(settings.appearance(), acceptsColorFrom(player)));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The player disconnected between the check and the send.
        }
    }

    static byte[] payload(PluginSettings.Appearance appearance, boolean playerColor) {
        JsonObject values = new JsonObject();
        for (Map.Entry<String, Object> entry : appearance.settings().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean flag) {
                values.addProperty(entry.getKey(), flag);
            } else if (value instanceof Number number) {
                values.addProperty(entry.getKey(), number);
            } else {
                values.addProperty(entry.getKey(), String.valueOf(value));
            }
        }
        JsonObject root = new JsonObject();
        root.add("appearance", values);
        root.addProperty("playerColor", playerColor);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Handles a {@code crowbar:player_color} message; the plugin's channel listener calls this. */
    public void onColorMessage(Player player, byte[] message) {
        if (!acceptsColorFrom(player)) return;
        ColorChoice choice = parseColor(message);
        if (choice == null) return;
        UUID id = player.getUniqueId();
        Integer current = colors.get(id);
        if (choice.rgb() == null ? current == null : choice.rgb().equals(current)) return;
        colors.set(id, choice.rgb());
    }

    /** @param rgb the chosen colour, or {@code null} to go back to the default */
    record ColorChoice(Integer rgb) {
    }

    /** Reads {@code {"color":"#RRGGBB"}} or {@code {"color":"reset"}}; {@code null} if malformed. */
    static ColorChoice parseColor(byte[] message) {
        if (message.length > MAX_COLOR_MESSAGE_BYTES) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return null;
            JsonElement color = root.getAsJsonObject().get("color");
            if (color == null || !color.isJsonPrimitive()) return null;
            String value = color.getAsString().trim();
            if (value.equalsIgnoreCase("reset")) return new ColorChoice(null);
            Integer rgb = PluginSettings.parseColor(value);
            return rgb != null ? new ColorChoice(rgb) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
