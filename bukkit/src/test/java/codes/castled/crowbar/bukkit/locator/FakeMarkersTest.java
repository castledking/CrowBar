package codes.castled.crowbar.bukkit.locator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeMarkersTest {
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void eachNameGetsItsOwnDefaultSkin() {
        Random random = new Random(1);
        for (int skin = 0; skin < FakeMarkers.NAMES.length; skin++) {
            UUID uuid = FakeMarkers.uuidForSkin(skin, random);
            // Minecraft's DefaultPlayerSkin.get: floorMod(hashCode, 18) into slim then wide skins.
            assertEquals(skin, Math.floorMod(uuid.hashCode(), 18) % 9, FakeMarkers.NAMES[skin]);
        }
    }

    @Test
    void namesRepeatWithNumbersPastTheNinthSkin() {
        List<FakeMarkers.Marker> markers = new FakeMarkers().spawn(VIEWER, WORLD, 0, 64, 0, 11, 0, new Random(2));

        assertEquals("Alex", markers.get(0).name());
        assertEquals("Zuri", markers.get(8).name());
        assertEquals("Alex2", markers.get(9).name());
        assertEquals("Ari2", markers.get(10).name());
    }

    @Test
    void markersStayWithinTheLocatorRange() {
        FakeMarkers fakes = new FakeMarkers();
        for (FakeMarkers.Marker marker : fakes.spawn(VIEWER, WORLD, 100, 64, -100, 50, 200, new Random(3))) {
            double distance = Math.hypot(marker.x() - 100, marker.z() + 100);
            assertTrue(distance >= 12 && distance <= 190, "distance " + distance);
            assertEquals(WORLD, marker.world());
        }
    }

    @Test
    void spawningReplacesAndClearRemoves() {
        FakeMarkers fakes = new FakeMarkers();
        fakes.spawn(VIEWER, WORLD, 0, 64, 0, 6, 0, new Random(4));
        fakes.spawn(VIEWER, WORLD, 0, 64, 0, 2, 0, new Random(5));
        assertEquals(2, fakes.get(VIEWER).size());

        assertTrue(fakes.clear(VIEWER));
        assertTrue(fakes.get(VIEWER).isEmpty());
        assertFalse(fakes.clear(VIEWER));
    }
}
