package codes.castled.crowbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorBarVisibilityTest {
    @Test
    void xpWindowSuppressesVanillaLocatorMarkers() {
        assertTrue(LocatorBarVisibility.shouldCancelVanilla(
                true, false, true, false, true, false, false, false));
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

    @Test
    void selectedClaimsAreAnIndependentRenderSource() {
        assertTrue(LocatorBarVisibility.hasRenderSource(
                false, false, false, true));
    }

    @Test
    void activeClaimModeMakesCrowBarOwnTheVanillaBar() {
        assertTrue(LocatorBarVisibility.shouldCancelVanilla(
                false, false, true, false, false, true, false, false));
    }

    @Test
    void activeClaimModeHidesPlayerMarkers() {
        assertFalse(LocatorBarVisibility.shouldRenderPlayers(true));
    }

    @Test
    void disabledClaimModeShowsPlayerMarkers() {
        assertTrue(LocatorBarVisibility.shouldRenderPlayers(false));
    }

    @Test
    void namesAndDistancesIndependentlyEnableTheSharedLabelLayer() {
        assertTrue(LocatorBarVisibility.shouldRenderLabels(true, false));
        assertTrue(LocatorBarVisibility.shouldRenderLabels(false, true));
        assertFalse(LocatorBarVisibility.shouldRenderLabels(false, false));
    }

    @Test
    void skinsOnlyRenderInPlayerModeWhenEnabled() {
        assertTrue(LocatorBarVisibility.shouldRenderPlayerSkins(true, true));
        assertFalse(LocatorBarVisibility.shouldRenderPlayerSkins(true, false));
        assertFalse(LocatorBarVisibility.shouldRenderPlayerSkins(false, true));
    }
}
