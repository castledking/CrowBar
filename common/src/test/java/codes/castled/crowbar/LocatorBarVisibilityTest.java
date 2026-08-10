package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorBarVisibilityTest {
    @Test
    void xpWindowSuppressesVanillaLocatorMarkers() {
        assertTrue(LocatorBarVisibility.shouldCancelVanilla(
                true, false, true, false, true, false, false));
    }

    @Test
    void qualifyingClaimKeepsCrowBarVisibleWhenNobodyElseIsOnline() {
        assertTrue(LocatorBarVisibility.shouldRenderCrowBar(
                false, false, true, true, false));
    }

    @Test
    void noQualifyingClaimLeavesCrowBarHiddenWhenNobodyElseIsOnline() {
        assertFalse(LocatorBarVisibility.shouldRenderCrowBar(
                false, false, false, true, false));
    }

    @Test
    void unrelatedVanillaWaypointCanStillKeepOwnedBarVisible() {
        assertTrue(LocatorBarVisibility.shouldRenderCrowBar(
                false, false, false, true, true));
    }

    @Test
    void xpWindowHidesCrowBarClaimsAndBackground() {
        assertFalse(LocatorBarVisibility.shouldRenderCrowBar(
                true, false, true, true, false));
    }
}
