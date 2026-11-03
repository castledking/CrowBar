package codes.castled.crowbar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimWaypointsTest {
    /** GPExpansion's modes, in the order its /claim bar lists them. */
    private static final String GPX_MODES = "[\"own\",\"trusted\",\"both\",\"off\"]";
    private static final UUID OWNED_MARKER = UUID.fromString("00000000-0000-3000-8000-000000000001");

    /** Claim 1 is owned, 2 trusted, 3 published, each listing the GPExpansion modes that show it. */
    private static final String GPX_CLAIMS = "{\"claims\":["
            + "{\"id\":\"1\",\"name\":\"Home\",\"owned\":true,\"x\":1,\"y\":64,\"z\":1,"
            + "\"uuid\":\"" + OWNED_MARKER + "\",\"modes\":[\"own\",\"trusted\",\"both\"]},"
            + "{\"id\":\"2\",\"owned\":false,\"x\":2,\"z\":2,\"modes\":[\"trusted\",\"both\"]},"
            + "{\"id\":\"3\",\"owned\":false,\"x\":3,\"z\":3,\"modes\":[\"both\"]}]}";
    /** The same claims as a server that leaves the mode to the client sends them. */
    private static final String PLAIN_CLAIMS = "{\"claims\":["
            + "{\"id\":\"1\",\"owned\":true,\"x\":1,\"z\":1},"
            + "{\"id\":\"2\",\"owned\":false,\"x\":2,\"z\":2}]}";

    private final List<String> sent = new ArrayList<>();
    private long now = 1_000_000;

    @BeforeEach
    void setUp() {
        ClaimWaypoints.reset();
        ClaimWaypoints.localMode = ClaimWaypoints.TRUSTED;
        ClaimWaypoints.clock = () -> now;
        ClaimWaypoints.setModeSender(payload -> sent.add(new String(payload, StandardCharsets.UTF_8)));
    }

    @AfterEach
    void tearDown() {
        ClaimWaypoints.reset();
        ClaimWaypoints.localMode = ClaimWaypoints.TRUSTED;
        ClaimWaypoints.clock = System::currentTimeMillis;
        ClaimWaypoints.setModeSender(payload -> false);
    }

    @Test
    void claimBarOffStillLetsTheKeyTurnClaimsBackOn() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);
        assertEquals(Set.of(), shown(), "off draws nothing");
        assertFalse(ClaimWaypoints.isModeActive());

        assertEquals(ClaimWaypoints.KeyResult.CHANGED, ClaimWaypoints.pressKey(),
                "off is a mode, not an empty claim list, so the key must not say no claims were found");

        assertEquals("own", ClaimWaypoints.mode());
        assertEquals(Set.of("1"), shown());
        assertEquals("Claim waypoints: owned", ClaimWaypoints.modeMessage());
        assertEquals(List.of("{\"mode\":\"own\",\"seq\":1}"), sent);
    }

    @Test
    void theKeyCyclesTheServersModesAndShowsWhatEachOneLists() {
        serverMode("own", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);
        assertEquals(Set.of("1"), shown());

        ClaimWaypoints.pressKey();
        assertEquals(Set.of("1", "2"), shown());
        assertEquals("Claim waypoints: owned + trusted", ClaimWaypoints.modeMessage());
        ClaimWaypoints.pressKey();
        assertEquals(Set.of("1", "2", "3"), shown());
        assertEquals("Claim waypoints: owned + trusted + published", ClaimWaypoints.modeMessage());
        ClaimWaypoints.pressKey();
        assertEquals(Set.of(), shown());
        assertEquals("Claim waypoints: off", ClaimWaypoints.modeMessage());
        ClaimWaypoints.pressKey();
        assertEquals("own", ClaimWaypoints.mode(), "off wraps around to the first mode");

        assertEquals(List.of(
                "{\"mode\":\"trusted\",\"seq\":1}",
                "{\"mode\":\"both\",\"seq\":2}",
                "{\"mode\":\"off\",\"seq\":3}",
                "{\"mode\":\"own\",\"seq\":4}"), sent);
    }

    @Test
    void aCommandOnTheServerMovesTheKeyToo() {
        serverMode("both", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);

        serverMode("off", 0);
        assertEquals("off", ClaimWaypoints.mode());
        assertEquals(Set.of(), shown());

        ClaimWaypoints.pressKey();
        assertEquals("own", ClaimWaypoints.mode(), "the key carries on from the command's mode");
    }

    @Test
    void anOlderConfirmationDoesNotFlickTheBarBack() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);
        ClaimWaypoints.pressKey();
        ClaimWaypoints.pressKey();
        assertEquals("trusted", ClaimWaypoints.mode());

        serverMode("own", 1);
        assertEquals("trusted", ClaimWaypoints.mode(), "request 2 is still on its way");

        serverMode("trusted", 2);
        assertEquals("trusted", ClaimWaypoints.mode());
    }

    @Test
    void aRefusedRequestFallsBackToTheServersMode() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);
        ClaimWaypoints.pressKey();

        serverMode("off", 1);

        assertEquals("off", ClaimWaypoints.mode());
        assertEquals(Set.of(), shown());
    }

    @Test
    void aServerThatLostCountIsBelievedAgainAfterTheTimeout() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);
        ClaimWaypoints.pressKey();

        // A plugin reload forgets the requests it handled, so its acks restart at 0.
        serverMode("off", 0);
        assertEquals("own", ClaimWaypoints.mode());

        now += ClaimWaypoints.REQUEST_TIMEOUT_MILLIS;
        serverMode("trusted", 0);
        assertEquals("trusted", ClaimWaypoints.mode());
    }

    @Test
    void emptyServerModesHandTheKeyBackToTheClient() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);

        ClaimWaypoints.onServerMode("{\"modes\":[]}");

        assertFalse(ClaimWaypoints.isServerManaged());
        assertEquals("trusted", ClaimWaypoints.mode());
        ClaimWaypoints.pressKey();
        assertTrue(sent.isEmpty(), "a client-picked mode is never sent");
    }

    @Test
    void withoutServerModesTheKeyCyclesLocallyAsBefore() {
        ClaimWaypoints.onClaimData(PLAIN_CLAIMS);
        assertEquals(Set.of("1", "2"), shown(), "owned + trusted shows everything sent");

        assertEquals(ClaimWaypoints.KeyResult.CHANGED, ClaimWaypoints.pressKey());
        assertEquals("off", ClaimWaypoints.mode());
        assertEquals(Set.of(), shown());
        ClaimWaypoints.pressKey();
        assertEquals("own", ClaimWaypoints.mode());
        assertEquals(Set.of("1"), shown());
        ClaimWaypoints.pressKey();
        assertEquals("trusted", ClaimWaypoints.mode());

        assertTrue(sent.isEmpty());
    }

    @Test
    void resetForgetsTheServerButKeepsTheLocalPick() {
        ClaimWaypoints.onClaimData(PLAIN_CLAIMS);
        ClaimWaypoints.pressKey();
        ClaimWaypoints.pressKey();
        serverMode("both", 0);
        assertEquals("both", ClaimWaypoints.mode());

        ClaimWaypoints.reset();

        assertFalse(ClaimWaypoints.isServerManaged());
        assertFalse(ClaimWaypoints.hasDataReceived());
        assertEquals("own", ClaimWaypoints.mode());
        assertEquals(ClaimWaypoints.KeyResult.LOADING, ClaimWaypoints.pressKey());
    }

    @Test
    void noClaimsInThisWorldLeavesTheModeAlone() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData("{\"claims\":[]}");

        assertEquals(ClaimWaypoints.KeyResult.NO_CLAIMS, ClaimWaypoints.pressKey());
        assertEquals("off", ClaimWaypoints.mode());
        assertTrue(sent.isEmpty());
    }

    @Test
    void aModeWithNothingToShowSaysSo() {
        serverMode("off", 0);
        ClaimWaypoints.onClaimData("{\"claims\":[{\"id\":\"3\",\"x\":3,\"z\":3,\"modes\":[\"both\"]}]}");

        ClaimWaypoints.pressKey();

        assertEquals("Claim waypoints: owned (none found)", ClaimWaypoints.modeMessage());
        assertFalse(ClaimWaypoints.isModeActive());
    }

    @Test
    void unfamiliarServerModesKeepTheirNames() {
        ClaimWaypoints.onServerMode("{\"mode\":\"off\",\"modes\":[\"nearby\",\"off\"]}");
        ClaimWaypoints.onClaimData("{\"claims\":[{\"id\":\"9\",\"x\":0,\"z\":0,\"modes\":[\"nearby\"]}]}");

        ClaimWaypoints.pressKey();

        assertEquals("Claim waypoints: nearby", ClaimWaypoints.modeMessage());
        assertEquals(Set.of("9"), shown());
    }

    @Test
    void markerIdsSuppressTheVanillaDuplicates() {
        ClaimWaypoints.onClaimData(GPX_CLAIMS);

        assertTrue(ClaimWaypoints.isMarker(OWNED_MARKER));
        assertFalse(ClaimWaypoints.isMarker(UUID.randomUUID()));
        assertFalse(ClaimWaypoints.isMarker(null));
    }

    @Test
    void malformedPayloadsChangeNothing() {
        serverMode("own", 0);
        ClaimWaypoints.onClaimData(GPX_CLAIMS);

        ClaimWaypoints.onClaimData("not json");
        ClaimWaypoints.onClaimData("{\"claims\":{}}");
        ClaimWaypoints.onServerMode("{\"mode\":\"elsewhere\",\"modes\":[\"own\",\"off\"]}");
        ClaimWaypoints.onServerMode("[]");

        assertEquals("own", ClaimWaypoints.mode());
        assertEquals(Set.of("1"), shown());
    }

    @Test
    void oneBrokenClaimDoesNotHideTheRest() {
        List<ClaimWaypointData> claims = ClaimWaypoints.parseClaims("{\"claims\":["
                + "{\"id\":\"1\",\"x\":\"far\",\"z\":0},"
                + "\"not a claim\","
                + "{\"id\":\"2\",\"x\":0},"
                + "{\"id\":\"3\",\"x\":5,\"z\":6,\"color\":16711680,\"uuid\":\"not-a-uuid\"}]}");

        assertEquals(1, claims.size());
        ClaimWaypointData claim = claims.get(0);
        assertEquals("3", claim.id());
        assertEquals(0xFFFF0000, claim.color());
        assertNull(claim.markerId());
        assertTrue(claim.modes().isEmpty());
    }

    private void serverMode(String mode, int ack) {
        ClaimWaypoints.onServerMode("{\"mode\":\"" + mode + "\",\"modes\":" + GPX_MODES + ",\"ack\":" + ack + "}");
    }

    private static Set<String> shown() {
        return ClaimWaypoints.selected().stream().map(ClaimWaypointData::id).collect(Collectors.toSet());
    }
}
