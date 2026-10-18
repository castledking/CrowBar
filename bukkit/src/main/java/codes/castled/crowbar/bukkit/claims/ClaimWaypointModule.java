package codes.castled.crowbar.bukkit.claims;

import codes.castled.crowbar.bukkit.PluginSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes GriefPrevention claims to locator bars as bowtie markers. Ported from GPExpansion.
 *
 * <p>Claims reach clients two ways:
 * <ul>
 *   <li><b>CrowBar clients</b> get every claim they may see on {@code crowbar:claim_data}, with
 *       position, name and ownership, and draw it themselves at any distance.
 *   <li><b>Other clients</b> get vanilla {@code ClientboundTrackedWaypointPacket}s through
 *       {@link WaypointPacketBridge}, when {@code claim-waypoints.vanilla-markers} is on and the
 *       server supports it. The packet is self-contained, so no entity or loaded chunk is needed.
 * </ul>
 *
 * <p>GPExpansion's extras (custom names, colours, claim spawns, published claims) are not here:
 * claims are named after their owner, anchored at their centre, and shown to their owner and the
 * players trusted on them. Servers that want more run GPExpansion, and this module steps aside.
 *
 * <p>Every refresh is triggered by a discrete event (join, world change, claim and trust changes),
 * never by polling. {@link #rebuildAll()} computes each viewer's desired set and diffs it against
 * what that viewer was already sent, so rebuilding is cheap and idempotent.
 */
public final class ClaimWaypointModule {
    public static final String CHANNEL = "crowbar:claim_data";
    public static final String ADMIN_PERMISSION = "crowbar.claims.admin";

    private final Plugin plugin;
    private final PluginSettings.Claims settings;
    private final ClaimBarModeStore modes;
    private final ClaimWaypointListener listener;

    /** What each online player has on their vanilla locator bar, keyed by claim ID. */
    private final Map<UUID, Map<String, SentWaypoint>> sentByPlayer = new ConcurrentHashMap<>();
    private boolean loggedBridgeFailure;

    /** One waypoint as last sent to a viewer. Equality is the resend check. */
    private record SentWaypoint(UUID waypointId, int x, int y, int z) {
    }

    private record PreparedClaim(String id, Claim claim, Location anchor, String name, UUID waypointId) {
    }

    public ClaimWaypointModule(Plugin plugin, PluginSettings.Claims settings, ClaimBarModeStore modes) {
        this.plugin = plugin;
        this.settings = settings;
        this.modes = modes;
        this.listener = new ClaimWaypointListener(plugin, this);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        rebuildAll();
    }

    /** Withdraws every vanilla marker so clients are not left with stale ones after a reload. */
    public void stop() {
        org.bukkit.event.HandlerList.unregisterAll(listener);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            withdrawAll(viewer);
        }
        sentByPlayer.clear();
    }

    void handleQuit(Player player) {
        sentByPlayer.remove(player.getUniqueId());
    }

    /** Whether players without CrowBar get vanilla markers on this server. */
    public boolean vanillaMarkersActive() {
        if (!settings.vanillaMarkers()) return false;
        if (WaypointPacketBridge.isAvailable()) return true;
        if (!loggedBridgeFailure) {
            loggedBridgeFailure = true;
            plugin.getLogger().info("Vanilla claim markers need Paper on Minecraft 1.21.6 or newer; "
                    + "only CrowBar clients will see claims. (" + WaypointPacketBridge.initError() + ")");
        }
        return false;
    }

    // ------------------------------------------------------------------ recomputation

    public void rebuildAll() {
        List<Player> viewers = new ArrayList<>(Bukkit.getOnlinePlayers());
        boolean sendPackets = vanillaMarkersActive();
        Map<UUID, List<PreparedClaim>> claimsByViewer = prepareClaimsFor(viewers);

        Set<UUID> online = new HashSet<>();
        for (Player viewer : viewers) {
            online.add(viewer.getUniqueId());
            applyViewer(viewer, claimsByViewer.getOrDefault(viewer.getUniqueId(), List.of()), sendPackets);
        }
        sentByPlayer.keySet().retainAll(online);
    }

    /** Refreshes one player, for joins, channel registration and world changes. */
    public void rebuildViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        boolean sendPackets = vanillaMarkersActive();
        applyViewer(viewer, prepareClaimsFor(List.of(viewer)).getOrDefault(viewer.getUniqueId(), List.of()), sendPackets);
    }

    private void applyViewer(Player viewer, List<PreparedClaim> claims, boolean sendPackets) {
        sendClaimData(viewer, claims, sendPackets);
        if (sendPackets) {
            syncViewer(viewer, claims);
        } else {
            // Markers can be switched off by a reload: withdraw anything sent before.
            withdrawAll(viewer);
        }
    }

    /** Walks GriefPrevention's claims once and prepares each claim at most once. */
    private Map<UUID, List<PreparedClaim>> prepareClaimsFor(List<Player> viewers) {
        Map<UUID, List<PreparedClaim>> result = new HashMap<>();
        for (Player viewer : viewers) {
            result.put(viewer.getUniqueId(), new ArrayList<>());
        }
        if (viewers.isEmpty() || GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null) {
            return result;
        }

        // Some GriefPrevention builds list subdivisions in getClaims() and others only under their
        // parent, so walk both and remember what has been seen.
        Set<Claim> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Claim claim : GriefPrevention.instance.dataStore.getClaims()) {
            if (claim == null) continue;
            if (claim.parent == null || settings.subdivisions()) {
                collect(claim, viewers, result, seen);
            }
            if (settings.subdivisions()) {
                for (Claim child : claim.children) {
                    collect(child, viewers, result, seen);
                }
            }
        }
        return result;
    }

    private void collect(Claim claim, List<Player> viewers, Map<UUID, List<PreparedClaim>> result, Set<Claim> seen) {
        if (claim == null || !claim.inDataStore || !seen.add(claim)) return;
        List<Player> eligible = new ArrayList<>();
        for (Player viewer : viewers) {
            if (canViewClaim(viewer, claim)) eligible.add(viewer);
        }
        if (eligible.isEmpty()) return;

        PreparedClaim prepared = prepareClaim(claim);
        if (prepared == null) return;
        for (Player viewer : eligible) {
            result.get(viewer.getUniqueId()).add(prepared);
        }
    }

    private PreparedClaim prepareClaim(Claim claim) {
        Location anchor = anchorFor(claim);
        if (anchor == null || anchor.getWorld() == null) return null;
        String id = String.valueOf(claim.getID());
        return new PreparedClaim(id, claim, anchor, displayNameFor(claim, id), waypointUuid(id));
    }

    /** Diffs one viewer's desired vanilla markers against what they were already sent. */
    private void syncViewer(Player viewer, List<PreparedClaim> claims) {
        Map<String, SentWaypoint> desired = new HashMap<>();
        UUID viewerId = viewer.getUniqueId();
        // CrowBar clients draw claims themselves and suppress these by UUID, so the mode is only
        // meaningful for vanilla clients.
        ClaimBarMode mode = isCrowBarClient(viewer) ? ClaimBarMode.TRUSTED : modes.get(viewerId);
        long range = settings.vanillaMarkerRange();

        if (locatorBarEnabled(viewer.getWorld())) {
            for (PreparedClaim claim : claims) {
                if (!allowedByMode(mode, claim.claim(), viewerId)) continue;
                Location anchor = claim.anchor();
                if (!anchor.getWorld().equals(viewer.getWorld())) continue;
                if (anchor.distanceSquared(viewer.getLocation()) > (double) range * range) continue;
                desired.put(claim.id(), new SentWaypoint(claim.waypointId(),
                        anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ()));
            }
        }

        Map<String, SentWaypoint> sent = sentByPlayer.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());
        for (Map.Entry<String, SentWaypoint> entry : new ArrayList<>(sent.entrySet())) {
            if (!desired.containsKey(entry.getKey())) {
                WaypointPacketBridge.sendRemove(viewer, entry.getValue().waypointId());
                sent.remove(entry.getKey());
            }
        }
        for (Map.Entry<String, SentWaypoint> entry : desired.entrySet()) {
            if (!entry.getValue().equals(sent.get(entry.getKey()))) {
                // The client keys waypoints by identifier, so an add for a known one replaces it.
                SentWaypoint waypoint = entry.getValue();
                WaypointPacketBridge.sendAdd(viewer, waypoint.waypointId(), waypoint.x(), waypoint.y(), waypoint.z(), null);
                sent.put(entry.getKey(), waypoint);
            }
        }
    }

    /**
     * The locatorBar game rule gates the vanilla bar; sending markers past it would override an
     * explicit server choice. Looked up by name because the rule only exists from 1.21.6, and
     * 1.21.11 renamed it when game rules became registry entries.
     */
    @SuppressWarnings("unchecked")
    private static boolean locatorBarEnabled(World world) {
        for (String name : LOCATOR_BAR_RULE_NAMES) {
            GameRule<?> rule;
            try {
                rule = GameRule.getByName(name);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (rule != null && rule.getType() == Boolean.class) {
                return !Boolean.FALSE.equals(world.getGameRuleValue((GameRule<Boolean>) rule));
            }
        }
        return true;
    }

    private static final String[] LOCATOR_BAR_RULE_NAMES = {"locatorBar", "locator_bar", "minecraft:locator_bar"};

    private void withdrawAll(Player viewer) {
        Map<String, SentWaypoint> sent = sentByPlayer.remove(viewer.getUniqueId());
        if (sent == null) return;
        for (SentWaypoint waypoint : sent.values()) {
            WaypointPacketBridge.sendRemove(viewer, waypoint.waypointId());
        }
    }

    /** Drops a claim's marker from every viewer, for deletion or abandonment. */
    void removeClaim(long claimId) {
        String key = String.valueOf(claimId);
        for (Map.Entry<UUID, Map<String, SentWaypoint>> entry : sentByPlayer.entrySet()) {
            SentWaypoint sent = entry.getValue().remove(key);
            if (sent == null) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) WaypointPacketBridge.sendRemove(viewer, sent.waypointId());
        }
    }

    /**
     * A deterministic marker identifier per claim.
     *
     * <p>Name-based UUIDs cannot collide with the random ones real entities use, and determinism
     * lets CrowBar be told the same identifier so it hides the vanilla duplicate of a claim it draws.
     * The namespace differs from GPExpansion's so the two can never fight over a marker.
     */
    private static UUID waypointUuid(String claimId) {
        return UUID.nameUUIDFromBytes(("crowbar:claim-waypoint:" + claimId).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ visibility

    /**
     * Owners and anyone trusted on a claim see it; admin claims need the admin setting and
     * permission. {@link ClaimPermission#Access} is the weakest trust and is implied by every
     * higher level, so one check covers them all.
     */
    private boolean canViewClaim(Player viewer, Claim claim) {
        UUID owner = claim.getOwnerID();
        if (owner == null) {
            return settings.adminClaims() && viewer.hasPermission(ADMIN_PERMISSION);
        }
        UUID viewerId = viewer.getUniqueId();
        return owner.equals(viewerId) || claim.hasExplicitPermission(viewerId, ClaimPermission.Access);
    }

    /** Admin claims (no owner) never get vanilla markers, whichever mode is active. */
    private static boolean allowedByMode(ClaimBarMode mode, Claim claim, UUID viewerId) {
        UUID owner = claim.getOwnerID();
        if (owner == null) return false;
        return switch (mode) {
            case OFF -> false;
            case OWN -> owner.equals(viewerId);
            case TRUSTED -> owner.equals(viewerId) || claim.hasExplicitPermission(viewerId, ClaimPermission.Access);
        };
    }

    /**
     * The centre of the claim's bounds. Y comes from the bounds rather than a surface scan so this
     * never touches chunk data.
     */
    private static Location anchorFor(Claim claim) {
        Location lesser = claim.getLesserBoundaryCorner();
        Location greater = claim.getGreaterBoundaryCorner();
        if (lesser == null || greater == null || lesser.getWorld() == null) return null;

        double x = (Math.min(lesser.getBlockX(), greater.getBlockX()) + Math.max(lesser.getBlockX(), greater.getBlockX())) / 2.0 + 0.5;
        double z = (Math.min(lesser.getBlockZ(), greater.getBlockZ()) + Math.max(lesser.getBlockZ(), greater.getBlockZ())) / 2.0 + 0.5;
        double y = (Math.min(lesser.getBlockY(), greater.getBlockY()) + Math.max(lesser.getBlockY(), greater.getBlockY())) / 2.0;
        return new Location(lesser.getWorld(), x, y, z);
    }

    private static String displayNameFor(Claim claim, String claimId) {
        if (claim.getOwnerID() == null) return "Admin Claim";
        String owner = claim.getOwnerName();
        return owner != null && !owner.isBlank() ? owner + "'s Claim" : "Claim #" + claimId;
    }

    /** CrowBar registers a receiver for the claim channel on join; nothing else does. */
    private static boolean isCrowBarClient(Player player) {
        return player.getListeningPluginChannels().contains(CHANNEL);
    }

    // ------------------------------------------------------------------ CrowBar payload

    /**
     * Tells a CrowBar client what each claim it may see is called and where it is.
     *
     * <p>Scoped per viewer so claim names and locations never leak to players without access.
     * Clients without CrowBar never registered the channel and are skipped.
     */
    private void sendClaimData(Player viewer, List<PreparedClaim> claims, boolean vanillaPackets) {
        if (!isCrowBarClient(viewer)) return;

        JsonArray entries = new JsonArray();
        for (PreparedClaim claim : claims) {
            Location anchor = claim.anchor();
            // A claim in another world cannot sit on a bar measured from the viewer.
            if (!anchor.getWorld().equals(viewer.getWorld())) continue;

            JsonObject json = new JsonObject();
            json.addProperty("id", claim.id());
            json.addProperty("name", claim.name());
            json.addProperty("owned", viewer.getUniqueId().equals(claim.claim().getOwnerID()));
            json.addProperty("x", anchor.getX());
            json.addProperty("y", anchor.getY());
            json.addProperty("z", anchor.getZ());
            // Present only when the claim also goes out as a vanilla marker, so CrowBar can hide
            // that duplicate.
            if (vanillaPackets) {
                json.addProperty("uuid", claim.waypointId().toString());
            }
            entries.add(json);
        }

        JsonObject root = new JsonObject();
        root.add("claims", entries);
        try {
            viewer.sendPluginMessage(plugin, CHANNEL, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
        }
    }
}
