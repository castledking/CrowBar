package codes.castled.crowbar;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the player positions that servers running Player Locator Plus send, so CrowBar works on
 * those servers the way it does with Allium or the CrowBar plugin.
 *
 * <p>Player Locator Plus sends {@code player-locator-plus:player_locations_v2} to every client. Each
 * packet holds changes only: a list of updated players, a list of players to remove, and whether to
 * forget everyone first. A player's position is relative to the receiving player, as a direction
 * and a distance; the distance is zero when the server withholds distances.
 *
 * <pre>
 * VarInt count, then per player: UUID (two longs), direction (three floats), distance (float),
 *                                colour (int, 0xRRGGBB)
 * VarInt count, then per removal: UUID (two longs)
 * boolean fullReset
 * </pre>
 */
public final class PlayerLocatorPlusData {
    public static final String NAMESPACE = "player-locator-plus";
    public static final String PATH = "player_locations_v2";
    /** Player Locator Plus's mod id, which would register the same channel itself. */
    public static final String MOD_ID = "player-locator-plus";

    /** Where a bearing-only player is placed, matching the CrowBar plugin's nominal distance. */
    static final double BEARING_ONLY_DISTANCE = 1000.0D;
    private static final int UUID_BYTES = 16;
    private static final int LOCATION_BYTES = UUID_BYTES + 3 * 4 + 4 + 4;

    record Location(UUID uuid, float dx, float dy, float dz, float distance, int color) {
    }

    record Update(List<Location> locations, List<UUID> removals, boolean fullReset) {
    }

    private PlayerLocatorPlusData() {
    }

    /**
     * Applies one packet to {@code players}, turning each relative position into a world position
     * from where the receiving player stands now.
     *
     * @return whether the packet was well formed; a malformed packet changes nothing
     */
    public static boolean apply(byte[] data, double selfX, double selfY, double selfZ, Map<UUID, AlliumPlayerData> players) {
        Update update;
        try {
            update = decode(ByteBuffer.wrap(data));
        } catch (BufferUnderflowException | IllegalArgumentException e) {
            return false;
        }

        if (update.fullReset()) {
            players.values().removeIf(player -> !player.fake);
        }
        for (UUID removed : update.removals()) {
            players.remove(removed);
        }
        for (Location location : update.locations()) {
            AlliumPlayerData player = toPlayerData(location, selfX, selfY, selfZ);
            if (player != null) {
                players.put(player.uuid, player);
            }
        }
        return true;
    }

    static AlliumPlayerData toPlayerData(Location location, double selfX, double selfY, double selfZ) {
        double length = Math.sqrt(location.dx() * location.dx() + location.dy() * location.dy() + location.dz() * location.dz());
        // Two players standing on the same spot have no direction, and NaN would poison the bar maths.
        if (!(length > 1.0E-6D) || !Float.isFinite(location.distance())) return null;

        boolean bearingOnly = location.distance() <= 0f;
        double distance = bearingOnly ? BEARING_ONLY_DISTANCE : location.distance();
        double scale = distance / length;
        return new AlliumPlayerData(location.uuid(),
                selfX + location.dx() * scale,
                selfY + location.dy() * scale,
                selfZ + location.dz() * scale,
                false, false, false,
                location.color() & 0xFFFFFF,
                bearingOnly, null, false);
    }

    static Update decode(ByteBuffer buffer) {
        int count = readCount(buffer, LOCATION_BYTES);
        List<Location> locations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
            locations.add(new Location(uuid, buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getFloat(), buffer.getInt()));
        }

        int removals = readCount(buffer, UUID_BYTES);
        List<UUID> removed = new ArrayList<>(removals);
        for (int i = 0; i < removals; i++) {
            removed.add(new UUID(buffer.getLong(), buffer.getLong()));
        }

        boolean fullReset = buffer.get() != 0;
        return new Update(locations, removed, fullReset);
    }

    /** Reads a list length, refusing one longer than the bytes left could hold. */
    private static int readCount(ByteBuffer buffer, int elementBytes) {
        int count = readVarInt(buffer);
        if (count < 0 || (long) count * elementBytes > buffer.remaining()) {
            throw new IllegalArgumentException("Bad list length " + count);
        }
        return count;
    }

    private static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = buffer.get();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt too long");
    }
}
