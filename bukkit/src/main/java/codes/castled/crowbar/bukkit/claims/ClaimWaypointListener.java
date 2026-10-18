package codes.castled.crowbar.bukkit.claims;

import codes.castled.crowbar.bukkit.Scheduler;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives {@link ClaimWaypointModule} from claim and trust changes. Ported from GPExpansion.
 *
 * <p>Cancellable events are handled at {@link EventPriority#MONITOR} and skipped when cancelled,
 * so a denied trust change or vetoed resize does not move a marker. Claim changes are applied a
 * tick later: GriefPrevention fires resize and transfer before it finishes writing the new bounds
 * or owner, and bulk operations fire many events that one rebuild can cover.
 */
final class ClaimWaypointListener implements Listener {
    private final Plugin plugin;
    private final ClaimWaypointModule module;
    private final AtomicBoolean rebuildQueued = new AtomicBoolean();

    ClaimWaypointListener(Plugin plugin, ClaimWaypointModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private void rebuildNextTick() {
        if (!rebuildQueued.compareAndSet(false, true)) return;
        Scheduler.later(plugin, () -> {
            rebuildQueued.set(false);
            module.rebuildAll();
        }, 1L);
    }

    private void rebuildLater(Player player, long delay) {
        UUID id = player.getUniqueId();
        Scheduler.later(plugin, () -> module.rebuildViewer(plugin.getServer().getPlayer(id)), delay);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // A quick send, and a later one for clients whose channel registration lands after joining.
        rebuildLater(event.getPlayer(), 1L);
        rebuildLater(event.getPlayer(), 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegistered(PlayerRegisterChannelEvent event) {
        if (ClaimWaypointModule.CHANNEL.equals(event.getChannel())) {
            rebuildLater(event.getPlayer(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        // Claims are per world, and vanilla drops a player's waypoints on a dimension change.
        rebuildLater(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrustChanged(TrustChangedEvent event) {
        rebuildNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimCreated(ClaimCreatedEvent event) {
        rebuildNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimDeleted(ClaimDeletedEvent event) {
        // The claim has already left the data store, so a rebuild alone would never withdraw it.
        if (event.getClaim() != null && event.getClaim().getID() != null) {
            module.removeClaim(event.getClaim().getID());
        }
        rebuildNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimResized(ClaimResizeEvent event) {
        rebuildNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimTransferred(ClaimTransferEvent event) {
        rebuildNextTick();
    }
}
