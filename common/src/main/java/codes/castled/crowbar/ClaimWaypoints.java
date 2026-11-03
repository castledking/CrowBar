package codes.castled.crowbar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The claims the server says this player may see, and which of them the claim waypoint key shows.
 *
 * <p>Each loader feeds in {@code crowbar:claim_data} and {@code crowbar:claim_mode} payloads,
 * resets this on join and disconnect, passes on claim key presses, and supplies a
 * {@link ModeSender} that writes {@code crowbar:claim_mode}.
 *
 * <h2>Who picks the mode</h2>
 * A server that sends {@code crowbar:claim_mode} picks it. GPExpansion does, and its mode is the
 * one {@code /claim bar} sets, so the key and the command change the same setting: a press
 * switches at once and tells the server, which confirms. Elsewhere the key cycles this client's own
 * pick of off, owned and owned + trusted, and the server never hears of it.
 *
 * <h2>{@code crowbar:claim_mode}</h2>
 * <ul>
 *   <li>Server to client: {@code {"mode":"off","modes":["own","trusted","both","off"],"ack":3}}.
 *       The player's mode, the modes the key cycles through in order, and the last request the
 *       server has handled. An empty {@code modes} hands the choice back to the client.
 *   <li>Client to server: {@code {"mode":"own","seq":4}}, with {@code seq} counting from 1 on each
 *       connection.
 * </ul>
 * A client says it speaks this by registering the channel. The server then sends it every claim it
 * may see on {@code crowbar:claim_data}, each listing the modes that show it in {@code "modes"}, so
 * switching never waits for a new claim list.
 */
public final class ClaimWaypoints {
    public static final String MODE_CHANNEL_NAMESPACE = "crowbar";
    public static final String MODE_CHANNEL_PATH = "claim_mode";

    public static final String OFF = "off";
    public static final String OWN = "own";
    public static final String TRUSTED = "trusted";
    /** GPExpansion's mode that adds published claims. */
    public static final String BOTH = "both";

    /** The key's cycle when this client picks: from the default, owned + trusted, to off, then owned. */
    private static final List<String> LOCAL_MODES = List.of(OWN, TRUSTED, OFF);
    /** How long a request may go unconfirmed before the server's word counts again. */
    static final long REQUEST_TIMEOUT_MILLIS = 3_000;
    private static final int MAX_SERVER_MODES = 16;

    /** What a claim key press did, for the action bar. */
    public enum KeyResult {
        /** No claim data has arrived yet. */
        LOADING,
        /** There are no claims to show in this world, so the mode was left alone. */
        NO_CLAIMS,
        /** The mode changed; {@link #modeMessage()} says to what. */
        CHANGED
    }

    /** Sends a {@code crowbar:claim_mode} payload; returns whether the server can receive it. */
    @FunctionalInterface
    public interface ModeSender {
        boolean send(byte[] payload);
    }

    /** A {@code crowbar:claim_mode} message from the server. */
    record ServerMode(String mode, List<String> modes, int ack) {
    }

    private static volatile ModeSender modeSender = payload -> false;
    /** Package-private so tests can move time. */
    static volatile LongSupplier clock = System::currentTimeMillis;

    private static volatile List<ClaimWaypointData> claims = List.of();
    /** The claims the current mode shows, recomputed whenever the claims or the mode change. */
    private static volatile List<ClaimWaypointData> selected = List.of();
    private static volatile Set<UUID> markerIds = Set.of();
    private static volatile boolean dataReceived;

    /**
     * This client's own pick, used where the server does not pick. Kept across servers.
     * Package-private so tests can start from the default.
     */
    static volatile String localMode = TRUSTED;
    /** The server's modes in key order, or null while this client picks. */
    private static volatile List<String> serverModes;
    private static volatile String serverMode;
    /** The last request sent on this connection, and when. */
    private static int sentSeq;
    private static long sentAt;

    private ClaimWaypoints() {
    }

    public static void setModeSender(ModeSender sender) {
        modeSender = sender;
    }

    // ------------------------------------------------------------------ server messages

    /** Handles {@code crowbar:claim_data}. Each payload replaces the previous claim list. */
    public static synchronized void onClaimData(String json) {
        List<ClaimWaypointData> parsed = parseClaims(json);
        if (parsed == null) return;
        Set<UUID> ids = new HashSet<>();
        for (ClaimWaypointData claim : parsed) {
            if (claim.markerId() != null) ids.add(claim.markerId());
        }
        claims = List.copyOf(parsed);
        markerIds = Set.copyOf(ids);
        // Even an empty list proves the server is present and talking, which gates the key.
        dataReceived = true;
        refresh();
    }

    /** Handles {@code crowbar:claim_mode}. */
    public static synchronized void onServerMode(String json) {
        ServerMode message = parseServerMode(json);
        if (message == null) return;
        if (message.modes().isEmpty()) {
            serverModes = null;
            serverMode = null;
        } else {
            serverModes = message.modes();
            // While a newer request is on its way, its confirmation brings the mode; taking this
            // older one would flick the bar back for a moment. A server that never answers, or lost
            // count in a reload, is believed again once the request times out.
            boolean caughtUp = message.ack() >= sentSeq
                    || clock.getAsLong() - sentAt >= REQUEST_TIMEOUT_MILLIS;
            if (caughtUp || serverMode == null || !message.modes().contains(serverMode)) {
                serverMode = message.mode();
            }
        }
        refresh();
    }

    /** Forgets this server's claims and modes, on join and disconnect. Keeps the local pick. */
    public static synchronized void reset() {
        claims = List.of();
        markerIds = Set.of();
        dataReceived = false;
        serverModes = null;
        serverMode = null;
        sentSeq = 0;
        sentAt = 0;
        refresh();
    }

    // ------------------------------------------------------------------ the key

    /** The claim waypoint key: moves to the next mode, and tells a server that picks the mode. */
    public static synchronized KeyResult pressKey() {
        if (!dataReceived) return KeyResult.LOADING;
        if (claims.isEmpty()) return KeyResult.NO_CLAIMS;
        List<String> order = serverModes != null ? serverModes : LOCAL_MODES;
        String next = next(order, mode());
        if (serverModes != null) {
            serverMode = next;
            request(next);
        } else {
            localMode = next;
        }
        refresh();
        return KeyResult.CHANGED;
    }

    /** The action bar text after a mode change. */
    public static String modeMessage() {
        String mode = mode();
        String message = "Claim waypoints: " + label(mode);
        return OFF.equals(mode) || hasSelected() ? message : message + " (none found)";
    }

    private static void request(String mode) {
        sentSeq++;
        sentAt = clock.getAsLong();
        JsonObject root = new JsonObject();
        root.addProperty("mode", mode);
        root.addProperty("seq", sentSeq);
        try {
            // A refusal leaves the switch in place; the server's next message settles it.
            modeSender.send(root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
        }
    }

    static String next(List<String> order, String current) {
        // A mode the order lacks, such as a local pick on a server with other modes, starts over.
        return order.get((order.indexOf(current) + 1) % order.size());
    }

    static String label(String mode) {
        return switch (mode) {
            case OFF -> "off";
            case OWN -> "owned";
            case TRUSTED -> "owned + trusted";
            case BOTH -> "owned + trusted + published";
            default -> mode;
        };
    }

    // ------------------------------------------------------------------ what to draw

    /** The current mode: the server's when it picks, otherwise this client's. */
    public static String mode() {
        String server = serverMode;
        return serverModes != null && server != null ? server : localMode;
    }

    /** Whether the server picks the mode, so the key and the server's command share one setting. */
    public static boolean isServerManaged() {
        return serverModes != null;
    }

    /** True once the server has sent any claim data, which is how the key knows it is useful. */
    public static boolean hasDataReceived() {
        return dataReceived;
    }

    public static boolean hasAny() {
        return !claims.isEmpty();
    }

    /** Claims to draw, already narrowed to the current mode. */
    public static List<ClaimWaypointData> selected() {
        return selected;
    }

    public static boolean hasSelected() {
        return !selected.isEmpty();
    }

    /** True only when the current mode has claims to replace player markers with. */
    public static boolean isModeActive() {
        return LocatorBarVisibility.isClaimWaypointModeActive(dataReceived, !OFF.equals(mode()), hasSelected());
    }

    /** Whether a vanilla waypoint is a claim that is drawn from claim data instead. */
    public static boolean isMarker(UUID uuid) {
        return uuid != null && markerIds.contains(uuid);
    }

    private static void refresh() {
        String mode = mode();
        boolean managed = serverModes != null;
        List<ClaimWaypointData> result = new ArrayList<>();
        for (ClaimWaypointData claim : claims) {
            if (shows(claim, mode, managed)) result.add(claim);
        }
        selected = List.copyOf(result);
    }

    /**
     * Whether {@code mode} shows {@code claim}. A server that picks the mode lists the modes for each
     * claim. Otherwise owned shows owned claims, and any other mode but off shows everything sent.
     */
    static boolean shows(ClaimWaypointData claim, String mode, boolean managed) {
        if (OFF.equals(mode)) return false;
        if (managed && !claim.modes().isEmpty()) return claim.modes().contains(mode);
        return !OWN.equals(mode) || claim.owned();
    }

    // ------------------------------------------------------------------ parsing

    /** The claims in a {@code crowbar:claim_data} payload, or null when it is malformed. */
    static List<ClaimWaypointData> parseClaims(String json) {
        JsonArray entries;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement list = root.getAsJsonObject().get("claims");
            if (list == null || !list.isJsonArray()) return null;
            entries = list.getAsJsonArray();
        } catch (RuntimeException e) {
            return null;
        }

        List<ClaimWaypointData> result = new ArrayList<>();
        for (JsonElement entry : entries) {
            try {
                ClaimWaypointData claim = parseClaim(entry.getAsJsonObject());
                if (claim != null) result.add(claim);
            } catch (RuntimeException ignored) {
                // One malformed claim should not hide the rest.
            }
        }
        return result;
    }

    private static ClaimWaypointData parseClaim(JsonObject claim) {
        if (!claim.has("id") || !claim.has("x") || !claim.has("z")) return null;

        UUID markerId = null;
        if (claim.has("uuid")) {
            try {
                markerId = UUID.fromString(claim.get("uuid").getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Set<String> modes = new HashSet<>();
        JsonElement listed = claim.get("modes");
        if (listed != null && listed.isJsonArray()) {
            for (JsonElement mode : listed.getAsJsonArray()) {
                if (mode.isJsonPrimitive()) modes.add(mode.getAsString());
            }
        }

        return new ClaimWaypointData(
                claim.get("id").getAsString(),
                claim.has("name") ? claim.get("name").getAsString() : null,
                claim.get("x").getAsDouble(),
                claim.has("y") ? claim.get("y").getAsDouble() : 0,
                claim.get("z").getAsDouble(),
                claim.has("owned") && claim.get("owned").getAsBoolean(),
                markerId,
                claim.has("color") ? (0xFF000000 | (claim.get("color").getAsInt() & 0xFFFFFF)) : null,
                modes);
    }

    /** A {@code crowbar:claim_mode} payload, or null when it is malformed. */
    static ServerMode parseServerMode(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject object = root.getAsJsonObject();

            List<String> modes = new ArrayList<>();
            JsonElement listed = object.get("modes");
            if (listed != null && listed.isJsonArray()) {
                for (JsonElement mode : listed.getAsJsonArray()) {
                    if (!mode.isJsonPrimitive()) continue;
                    String id = mode.getAsString();
                    if (!id.isBlank() && !modes.contains(id) && modes.size() < MAX_SERVER_MODES) modes.add(id);
                }
            }
            JsonElement current = object.get("mode");
            String mode = current != null && current.isJsonPrimitive() ? current.getAsString() : null;
            JsonElement ack = object.get("ack");
            int handled = ack != null && ack.isJsonPrimitive() ? ack.getAsInt() : 0;

            // The key can only move on from a mode it knows.
            if (!modes.isEmpty() && !modes.contains(mode)) return null;
            return new ServerMode(mode, List.copyOf(modes), handled);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
