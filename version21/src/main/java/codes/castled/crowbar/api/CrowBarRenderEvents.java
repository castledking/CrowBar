package codes.castled.crowbar.api;

import codes.castled.crowbar.CrowBarState;

public final class CrowBarRenderEvents {
    public static void setLocatorBarSuppressed(String owner, boolean suppressed) {
        setLocatorBarSuppressed(owner, suppressed, true);
    }

    public static void setLocatorBarSuppressed(String owner, boolean suppressed, boolean keepVanillaLocatorBar) {
        CrowBarState.setExternalRenderSuppressed(owner, suppressed, keepVanillaLocatorBar);
    }

    private CrowBarRenderEvents() {
    }
}
