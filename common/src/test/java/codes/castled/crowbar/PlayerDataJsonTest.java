package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataJsonTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");

    @Test
    void readsAlliumsFormat() {
        Map<UUID, AlliumPlayerData> players = PlayerDataJson.parse("{\"players\":[{\"uuid\":\"" + STEVE
                + "\",\"x\":1.5,\"y\":64,\"z\":-3,\"wearingPumpkin\":false,\"sneaking\":true,\"vanished\":false,\"teamColor\":16733525}]}");

        AlliumPlayerData steve = players.get(STEVE);
        assertEquals(1.5, steve.x);
        assertEquals(-3, steve.z);
        assertTrue(steve.sneaking);
        assertEquals(0xFF5555, steve.teamColor);
        assertFalse(steve.bearingOnly);
        assertNull(steve.name);
        assertTrue(steve.expires);
    }

    @Test
    void optionalFieldsDefault() {
        Map<UUID, AlliumPlayerData> players = PlayerDataJson.parse("{\"players\":[{\"uuid\":\"" + STEVE
                + "\",\"x\":0,\"y\":0,\"z\":0,\"wearingPumpkin\":false,\"sneaking\":false,\"vanished\":false,"
                + "\"bearingOnly\":true,\"name\":\"Steve\"}]}");

        AlliumPlayerData steve = players.get(STEVE);
        assertEquals(0xFFFFFF, steve.teamColor);
        assertTrue(steve.bearingOnly);
        assertEquals("Steve", steve.name);
    }

    @Test
    void malformedEntriesAreSkippedNotFatal() {
        Map<UUID, AlliumPlayerData> players = PlayerDataJson.parse("{\"players\":["
                + "{\"uuid\":\"not-a-uuid\",\"x\":0,\"y\":0,\"z\":0,\"wearingPumpkin\":false,\"sneaking\":false,\"vanished\":false},"
                + "{\"uuid\":\"" + STEVE + "\",\"x\":0,\"y\":0},"
                + "{\"uuid\":\"" + ALEX + "\",\"x\":0,\"y\":0,\"z\":0,\"wearingPumpkin\":false,\"sneaking\":false,\"vanished\":false}]}");

        assertEquals(1, players.size());
        assertTrue(players.containsKey(ALEX));
    }

    @Test
    void notAnObjectIsRejected() {
        assertNull(PlayerDataJson.parse("[]"));
        assertNull(PlayerDataJson.parse("{oops"));
        assertTrue(PlayerDataJson.parse("{}").isEmpty());
    }

    @Test
    void alternatingAlliumAndFakeSnapshotsKeepBothKindsOfPlayer() {
        Map<UUID, AlliumPlayerData> players = new ConcurrentHashMap<>();
        assertTrue(PlayerDataJson.applySnapshot(snapshot(STEVE, false), players));
        assertTrue(PlayerDataJson.applySnapshot(snapshot(ALEX, true), players));
        assertEquals(2, players.size(), "Fake markers must not replace Allium players");

        for (int tick = 0; tick < 20; tick++) {
            assertTrue(PlayerDataJson.applySnapshot(snapshot(STEVE, false), players));
            assertTrue(players.containsKey(ALEX), "Allium updates must not erase fake markers");
            assertTrue(PlayerDataJson.applySnapshot(snapshot(ALEX, true), players));
            assertTrue(players.containsKey(STEVE));
        }
        assertTrue(players.get(ALEX).fake);
        assertFalse(players.get(STEVE).fake);
    }

    @Test
    void clearingFakeMarkersLeavesAlliumsPlayersAlone() {
        Map<UUID, AlliumPlayerData> players = new ConcurrentHashMap<>();
        PlayerDataJson.applySnapshot(snapshot(ALEX, true), players);
        PlayerDataJson.applySnapshot(snapshot(STEVE, false), players);

        assertTrue(PlayerDataJson.applySnapshot("{\"fakePlayers\":true,\"players\":[]}", players));
        assertEquals(java.util.Set.of(STEVE), players.keySet());
    }

    @Test
    void removingRealPlayersLeavesFakeMarkersAlone() {
        Map<UUID, AlliumPlayerData> players = new ConcurrentHashMap<>();
        PlayerDataJson.applySnapshot(snapshot(STEVE, false), players);
        PlayerDataJson.applySnapshot(snapshot(ALEX, true), players);

        assertTrue(PlayerDataJson.applySnapshot("{\"players\":[]}", players));
        assertEquals(java.util.Set.of(ALEX), players.keySet());
    }

    @Test
    void malformedSnapshotDoesNotEraseEitherKindOfPlayer() {
        Map<UUID, AlliumPlayerData> players = new ConcurrentHashMap<>();
        PlayerDataJson.applySnapshot(snapshot(STEVE, false), players);
        PlayerDataJson.applySnapshot(snapshot(ALEX, true), players);
        var before = Map.copyOf(players);

        assertFalse(PlayerDataJson.applySnapshot("{oops", players));
        assertFalse(PlayerDataJson.applySnapshot("{\"fakePlayers\":\"yes\",\"players\":[]}", players));
        assertEquals(before, players);
    }

    private static String snapshot(UUID id, boolean fake) {
        return "{\"fakePlayers\":" + fake + ",\"players\":[{\"uuid\":\"" + id
                + "\",\"x\":0,\"y\":64,\"z\":32,\"wearingPumpkin\":false,\"sneaking\":false,\"vanished\":false}]}";
    }
}
