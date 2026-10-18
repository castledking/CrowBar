package codes.castled.crowbar.bukkit.locator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorPrecisionTest {
    private static double distance(LocatorPrecision.Reported r, double x, double y, double z) {
        return Math.sqrt(Math.pow(r.x() - x, 2) + Math.pow(r.y() - y, 2) + Math.pow(r.z() - z, 2));
    }

    @Test
    void exactByDefault() {
        LocatorPrecision.Reported r = LocatorPrecision.EXACT.report(0, 64, 0, 123.4, 70, -567.8);

        assertEquals(123.4, r.x());
        assertEquals(70, r.y());
        assertEquals(-567.8, r.z());
        assertFalse(r.bearingOnly());
    }

    @Test
    void roundsDirectionButKeepsTheDistance() {
        LocatorPrecision precision = new LocatorPrecision(8, true, 0, 0);

        LocatorPrecision.Reported r = precision.report(10, 64, 10, 10 + 300, 64, 10 + 7);

        assertEquals(Math.hypot(300, 7), distance(r, 10, 64, 10), 1.0E-6);
        assertNotEquals(17, r.z(), 1.0E-3);
        // 8 steps per axis cannot represent the slight offset, so the point snaps onto the axis.
        assertEquals(10, r.z(), 1.0E-6);
    }

    @Test
    void roundsOnlyDistancesBeyondTheThreshold() {
        LocatorPrecision precision = new LocatorPrecision(0, true, 200, 50);

        assertEquals(150, distance(precision.report(0, 0, 0, 150, 0, 0), 0, 0, 0), 1.0E-6);
        assertEquals(350, distance(precision.report(0, 0, 0, 333, 0, 0), 0, 0, 0), 1.0E-6);
        assertEquals(1250, distance(precision.report(0, 0, 0, 0, 0, 1240), 0, 0, 0), 1.0E-6);
    }

    @Test
    void withoutDistanceOnlyTheBearingIsSent() {
        LocatorPrecision precision = new LocatorPrecision(0, false, 0, 0);

        LocatorPrecision.Reported r = precision.report(0, 64, 0, 0, 64, 40);

        assertTrue(r.bearingOnly());
        assertEquals(LocatorPrecision.BEARING_ONLY_DISTANCE, distance(r, 0, 64, 0), 1.0E-6);
        assertEquals(LocatorPrecision.BEARING_ONLY_DISTANCE, r.z() - 0, 1.0E-6);
    }

    @Test
    void aTargetOnTheViewerIsLeftAlone() {
        LocatorPrecision precision = new LocatorPrecision(300, false, 200, 50);

        LocatorPrecision.Reported r = precision.report(5, 5, 5, 5, 5, 5);

        assertEquals(5, r.x());
        assertFalse(r.bearingOnly());
    }
}
