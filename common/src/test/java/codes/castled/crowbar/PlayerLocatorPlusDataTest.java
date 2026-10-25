package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLocatorPlusDataTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");

    /** One location as Player Locator Plus writes it. */
    private record Loc(UUID uuid, float dx, float dy, float dz, float distance, int color) {
    }

    /** Encodes a packet the way Player Locator Plus's StreamCodec does (big-endian, VarInt lengths). */
    private static byte[] packet(List<Loc> locations, List<UUID> removals, boolean fullReset) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeVarInt(out, locations.size());
        for (Loc loc : locations) {
            out.writeLong(loc.uuid().getMostSignificantBits());
            out.writeLong(loc.uuid().getLeastSignificantBits());
            out.writeFloat(loc.dx());
            out.writeFloat(loc.dy());
            out.writeFloat(loc.dz());
            out.writeFloat(loc.distance());
            out.writeInt(loc.color());
        }
        writeVarInt(out, removals.size());
        for (UUID uuid : removals) {
            out.writeLong(uuid.getMostSignificantBits());
            out.writeLong(uuid.getLeastSignificantBits());
        }
        out.writeBoolean(fullReset);
        return bytes.toByteArray();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    @Test
    void relativePositionsBecomeWorldPositions() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        byte[] data = packet(List.of(new Loc(STEVE, 0f, 0f, 1f, 250f, 0x55FFFF)), List.of(), true);

        assertTrue(PlayerLocatorPlusData.apply(data, 100, 64, -20, players));

        AlliumPlayerData steve = players.get(STEVE);
        assertEquals(100, steve.x, 1e-6);
        assertEquals(64, steve.y, 1e-6);
        assertEquals(230, steve.z, 1e-6);
        assertEquals(0x55FFFF, steve.teamColor);
        assertFalse(steve.bearingOnly);
        // Player Locator Plus only sends changes, so a player standing still must not time out.
        assertFalse(steve.expires);
        assertFalse(steve.isExpired());
    }

    @Test
    void roundedDirectionsAreRenormalised() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        // 0.707 rounded to 1/300 steps does not have length exactly 1.
        byte[] data = packet(List.of(new Loc(STEVE, 0.7067f, 0f, 0.7067f, 100f, 0)), List.of(), false);

        PlayerLocatorPlusData.apply(data, 0, 0, 0, players);

        AlliumPlayerData steve = players.get(STEVE);
        assertEquals(100, Math.sqrt(steve.x * steve.x + steve.z * steve.z), 1e-3);
    }

    @Test
    void zeroDistanceMeansDirectionOnly() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        byte[] data = packet(List.of(new Loc(STEVE, 1f, 0f, 0f, 0f, 0)), List.of(), false);

        PlayerLocatorPlusData.apply(data, 0, 0, 0, players);

        AlliumPlayerData steve = players.get(STEVE);
        assertTrue(steve.bearingOnly);
        assertEquals(PlayerLocatorPlusData.BEARING_ONLY_DISTANCE, steve.x, 1e-6);
    }

    @Test
    void removalsAndFullResetsApplyInOrder() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        PlayerLocatorPlusData.apply(packet(List.of(
                new Loc(STEVE, 1f, 0f, 0f, 10f, 0),
                new Loc(ALEX, -1f, 0f, 0f, 10f, 0)), List.of(), false), 0, 0, 0, players);
        assertEquals(2, players.size());

        PlayerLocatorPlusData.apply(packet(List.of(), List.of(STEVE), false), 0, 0, 0, players);
        assertNull(players.get(STEVE));
        assertTrue(players.containsKey(ALEX));

        // A full reset forgets everyone not in the same packet.
        PlayerLocatorPlusData.apply(packet(List.of(new Loc(STEVE, 1f, 0f, 0f, 10f, 0)), List.of(), true), 0, 0, 0, players);
        assertEquals(1, players.size());
        assertTrue(players.containsKey(STEVE));
    }

    @Test
    void playersWithNoDirectionAreSkipped() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        PlayerLocatorPlusData.apply(packet(List.of(new Loc(STEVE, 0f, 0f, 0f, 0f, 0)), List.of(), false), 0, 0, 0, players);
        assertTrue(players.isEmpty());
    }

    @Test
    void fullResetPreservesPrivateFakeMarkers() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        players.put(ALEX, new AlliumPlayerData(ALEX, 0, 64, 10, false, false, false,
                0xFFFFFF, false, "Alex", true, true));
        PlayerLocatorPlusData.apply(packet(List.of(new Loc(STEVE, 1f, 0f, 0f, 10f, 0)),
                List.of(), true), 0, 0, 0, players);
        assertEquals(2, players.size());
        assertTrue(players.get(ALEX).fake);
        assertFalse(players.get(STEVE).fake);
    }

    @Test
    void malformedPacketsChangeNothing() throws IOException {
        Map<UUID, AlliumPlayerData> players = new HashMap<>();
        PlayerLocatorPlusData.apply(packet(List.of(new Loc(ALEX, 1f, 0f, 0f, 10f, 0)), List.of(), false), 0, 0, 0, players);

        byte[] full = packet(List.of(new Loc(STEVE, 1f, 0f, 0f, 10f, 0)), List.of(), true);
        assertFalse(PlayerLocatorPlusData.apply(Arrays.copyOf(full, full.length - 1), 0, 0, 0, players));
        // A list length far beyond the packet is refused before allocating for it.
        assertFalse(PlayerLocatorPlusData.apply(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x7F}, 0, 0, 0, players));

        assertEquals(1, players.size());
        assertTrue(players.containsKey(ALEX));
    }
}
