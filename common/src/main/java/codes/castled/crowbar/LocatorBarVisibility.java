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
            boolean integratedServer,
            boolean hasRenderablePlayers
    ) {
        // The XP bar and locator bar occupy the same HUD slot. During the XP peek window, suppress
        // vanilla too; otherwise server-provided locator dots can remain layered over the XP bar.
        if (xpBarVisible) return true;
        if (externallySuppressed) return !keepVanillaDuringExternalSuppression;
        if (selfViewEnabled) return true;
        if (alliumDataReceived) return true;
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
}
