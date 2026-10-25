package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.bukkit.PluginSettings;
import codes.castled.crowbar.bukkit.Scheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends CrowBar clients where the other players in their world are.
 *
 * <p>Ported from Allium's CrowBar sender, with Player Locator Plus's server settings on top. The
 * payload keeps Allium's format on {@code crowbar:player_data}, so every CrowBar release that
 * understands Allium understands this too. Unlike Allium, hidden players are filtered here rather
 * than flagged for the client to ignore, so their positions never leave the server.
 */
public final class PlayerLocatorModule implements Listener {
    public static final String CHANNEL = "crowbar:player_data";
    public static final String RECEIVE_PERMISSION = "crowbar.locator";

    private final Plugin plugin;
    private final PluginSettings settings;
    private final HidingRules hiding;
    private final PlayerColors colors;
    private final FakeMarkers fakes;
    private final boolean sendPlayers;
    private final Set<UUID> fakeRecipients = ConcurrentHashMap.newKeySet();
    private Scheduler.Task task;

    public PlayerLocatorModule(Plugin plugin, PluginSettings settings, PlayerColorStore colorStore, FakeMarkers fakes, boolean sendPlayers) {
        this.plugin = plugin;
        this.settings = settings;
        this.hiding = new HidingRules(settings.hiding());
        this.colors = new PlayerColors(settings.colors(), colorStore);
        this.fakes = fakes;
        this.sendPlayers = sendPlayers;
    }

    public void start() {
        if (task != null) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Scheduler.repeat(plugin, () -> broadcast(null), 20L, settings.locator().updateIntervalTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Remove private markers immediately on disable/reload, without clearing Allium's players.
        for (UUID id : fakeRecipients) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.isOnline() && isListening(viewer)) {
                sendSnapshot(viewer, new JsonArray(), true);
            }
        }
        fakeRecipients.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        fakes.clear(event.getPlayer().getUniqueId());
        fakeRecipients.remove(event.getPlayer().getUniqueId());
        // Send right away rather than waiting for the next update, so the dot disappears with them.
        broadcast(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegistered(PlayerRegisterChannelEvent event) {
        // The earliest point at which CrowBar has told the server it is listening.
        if (CHANNEL.equals(event.getChannel())) {
            UUID id = event.getPlayer().getUniqueId();
            Scheduler.later(plugin, () -> sendTo(Bukkit.getPlayer(id), null), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Fake players belong to the world they were made in.
        fakes.clear(id);
        Scheduler.later(plugin, () -> sendTo(Bukkit.getPlayer(id), null), 1L);
    }

    /** Whether {@code player}'s client listens for positions, so it would show fake players. */
    public static boolean isListening(Player player) {
        return player.getListeningPluginChannels().contains(CHANNEL);
    }

    /** Sends one player an update now, such as right after their fake players change. */
    public void refresh(Player viewer) {
        sendTo(viewer, null);
    }

    private void broadcast(UUID leaving) {
        Collection<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            if (viewer.getUniqueId().equals(leaving)) continue;
            sendTo(viewer, leaving);
        }
    }

    /** Sends one viewer the players they may see, leaving out {@code leaving} if set. */
    private void sendTo(Player viewer, UUID leaving) {
        if (viewer == null || !viewer.isOnline()) return;
        // Only clients with CrowBar listen on the channel; everyone else would discard the payload.
        if (!isListening(viewer)) return;
        if (!viewer.hasPermission(RECEIVE_PERMISSION)) {
            if (fakeRecipients.remove(viewer.getUniqueId())) sendSnapshot(viewer, new JsonArray(), true);
            return;
        }

        Location origin = viewer.getLocation();
        double maxDistance = settings.locator().maxDistance();
        if (sendPlayers) {
            JsonArray players = new JsonArray();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(viewer) || target.getUniqueId().equals(leaving)) continue;
                if (!target.getWorld().equals(viewer.getWorld())) continue;
                if (hiding.hides(viewer, target)) continue;

                Location at = target.getLocation();
                if (maxDistance > 0 && at.distanceSquared(origin) > maxDistance * maxDistance) continue;

                JsonObject entry = entry(origin, target.getUniqueId(), at.getX(), at.getY(), at.getZ(), colors.colorFor(viewer, target));
                // CrowBar requires these three fields. Anyone they would apply to is already filtered
                // out above, so they only ever describe a viewer allowed to see through them.
                entry.addProperty("wearingPumpkin", hiding.isDisguised(target));
                entry.addProperty("sneaking", target.isSneaking());
                players.add(entry);
            }
            sendSnapshot(viewer, players, false);
        }

        JsonArray players = new JsonArray();
        UUID world = viewer.getWorld().getUID();
        for (FakeMarkers.Marker fake : fakes.get(viewer.getUniqueId())) {
            if (!fake.world().equals(world)) continue;
            double dx = fake.x() - origin.getX();
            double dy = fake.y() - origin.getY();
            double dz = fake.z() - origin.getZ();
            if (maxDistance > 0 && dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) continue;
            JsonObject entry = entry(origin, fake.uuid(), fake.x(), fake.y(), fake.z(), colors.colorForFake(fake.uuid()));
            // Fake players are not in the tab list, where CrowBar would otherwise find the name.
            entry.addProperty("name", fake.name());
            players.add(entry);
        }

        // No fake traffic for normal players, including on servers that defer to Allium. An empty
        // snapshot is still needed once after clearing or moving outside the configured range.
        if (!players.isEmpty()) {
            fakeRecipients.add(viewer.getUniqueId());
            sendSnapshot(viewer, players, true);
        } else if (fakeRecipients.remove(viewer.getUniqueId())) {
            sendSnapshot(viewer, players, true);
        }
    }

    private void sendSnapshot(Player viewer, JsonArray players, boolean fake) {
        JsonObject root = new JsonObject();
        if (fake) root.addProperty("fakePlayers", true);
        root.add("players", players);
        try {
            viewer.sendPluginMessage(plugin, CHANNEL, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The viewer disconnected between the check and the send.
        }
    }

    /** One player entry, positioned as the precision settings allow. */
    private JsonObject entry(Location origin, UUID id, double x, double y, double z, int color) {
        LocatorPrecision.Reported reported = settings.precision()
                .report(origin.getX(), origin.getY(), origin.getZ(), x, y, z);
        JsonObject entry = new JsonObject();
        entry.addProperty("uuid", id.toString());
        entry.addProperty("x", reported.x());
        entry.addProperty("y", reported.y());
        entry.addProperty("z", reported.z());
        entry.addProperty("wearingPumpkin", false);
        entry.addProperty("sneaking", false);
        entry.addProperty("vanished", false);
        entry.addProperty("teamColor", color);
        if (reported.bearingOnly()) {
            entry.addProperty("bearingOnly", true);
        }
        return entry;
    }
}
