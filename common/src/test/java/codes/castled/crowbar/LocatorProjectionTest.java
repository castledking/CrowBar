package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LocatorProjectionTest {
    @Test
    void thirdPersonProjectionUsesCameraPositionInsteadOfPlayerBody() {
        // Camera is four blocks behind the player. Vanilla projects from that detached camera,
        // while the old CrowBar path projected from the player's body at (0, 0).
        double vanillaYaw = LocatorProjection.relativeYaw(0.0, -4.0, 0.0, 4.0, 10.0);
        double bodyBasedYaw = LocatorProjection.relativeYaw(0.0, 0.0, 0.0, 4.0, 10.0);

        assertEquals(-15.945395900922854, vanillaYaw, 1.0e-9);
        assertNotEquals(bodyBasedYaw, vanillaYaw, 1.0e-9);
    }

    @Test
    void relativeYawWrapsAcrossNorth() {
        assertEquals(1.0, LocatorProjection.normalizeRelativeYaw(361.0), 1.0e-9);
        assertEquals(-1.0, LocatorProjection.normalizeRelativeYaw(-361.0), 1.0e-9);
    }
}
