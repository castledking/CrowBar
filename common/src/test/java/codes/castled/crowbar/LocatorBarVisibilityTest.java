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
    void emptyClaimSelectionLeavesPlayerModeAndVanillaHudEnabled() {
        boolean claimModeActive = LocatorBarVisibility.isClaimWaypointModeActive(
                true, true, false);

        assertFalse(claimModeActive);
        assertTrue(LocatorBarVisibility.shouldRenderPlayers(claimModeActive));
        assertFalse(LocatorBarVisibility.shouldCancelVanilla(
                false, false, true, false, false,
                claimModeActive, false, false));
    }

    @Test
    void populatedClaimSelectionActivatesClaimMode() {
        assertTrue(LocatorBarVisibility.isClaimWaypointModeActive(
                true, true, true));
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

    @Test
    void playersHideFromTheBarLikeVanillaWaypoints() {
        assertFalse(LocatorBarVisibility.isHiddenFromLocator(false, false, false, false));
        assertTrue(LocatorBarVisibility.isHiddenFromLocator(true, false, false, false));
        assertTrue(LocatorBarVisibility.isHiddenFromLocator(false, true, false, false));
        assertTrue(LocatorBarVisibility.isHiddenFromLocator(false, false, true, false));
        assertTrue(LocatorBarVisibility.isHiddenFromLocator(false, false, false, true));
    }
}
