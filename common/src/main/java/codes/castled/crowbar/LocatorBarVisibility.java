package codes.castled.crowbar;

/** Pure visibility rules shared by each supported Minecraft implementation. */
public final class LocatorBarVisibility {
    private LocatorBarVisibility() {
    }

    public static boolean shouldCancelVanilla(
            boolean xpBarVisible,
            boolean externallySuppressed,
            boolean keepVanillaDuringExternalSuppression,
            boolean selfViewEnabled,
            boolean alliumDataReceived,
            boolean claimWaypointModeActive,
            boolean integratedServer,
            boolean hasRenderablePlayers
    ) {
        // The XP bar and locator bar occupy the same HUD slot. During the XP peek window, suppress
        // vanilla too; otherwise server-provided locator dots can remain layered over the XP bar.
        if (xpBarVisible) return true;
        if (externallySuppressed) return !keepVanillaDuringExternalSuppression;
        if (selfViewEnabled) return true;
        if (alliumDataReceived) return true;
        if (claimWaypointModeActive) return true;
        return integratedServer && hasRenderablePlayers;
    }

    public static boolean shouldRenderCrowBar(
            boolean xpBarVisible,
            boolean hasRenderablePlayers,
            boolean hasSelectedClaims,
            boolean crowBarOwnsVanillaBar,
            boolean hasNonClaimVanillaWaypoints
    ) {
        return !xpBarVisible && (hasRenderablePlayers
                || hasSelectedClaims
                || (crowBarOwnsVanillaBar && hasNonClaimVanillaWaypoints));
    }

    /** Whether any independent source can supply content for CrowBar's locator HUD. */
    public static boolean hasRenderSource(
            boolean alliumPlayerDataReceived,
            boolean integratedServer,
            boolean vanillaLocatorVisible,
            boolean hasSelectedClaims
    ) {
        return alliumPlayerDataReceived || integratedServer || vanillaLocatorVisible || hasSelectedClaims;
    }

    /** Player markers and claim markers are mutually exclusive locator modes. */
    public static boolean shouldRenderPlayers(boolean claimWaypointModeActive) {
        return !claimWaypointModeActive;
    }

    /** Names and distances share the same label layer for both players and claims. */
    public static boolean shouldRenderLabels(boolean namesEnabled, boolean distanceEnabled) {
        return namesEnabled || distanceEnabled;
    }

    public static boolean shouldRenderPlayerSkins(boolean playerModeActive, boolean skinsEnabled) {
        return playerModeActive && skinsEnabled;
    }
}
