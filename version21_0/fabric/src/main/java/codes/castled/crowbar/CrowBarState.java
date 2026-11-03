package codes.castled.crowbar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrowBarState {
    // What the renderers draw this tick, resolved from the config and any held keys by
    // syncFeatures(). Change the config instead of assigning these.
    public static boolean nameTagsEnabled = true;
    public static boolean skinsEnabled = true;
    public static boolean viewSelfEnabled = false;
    public static boolean showDistance = false;
    private static final Map<String, Boolean> externalRenderSuppressions = new ConcurrentHashMap<>();

    // Storage for Allium-provided player data
    public static final Map<UUID, AlliumPlayerData> alliumPlayerData = new ConcurrentHashMap<>();
    public static boolean alliumDataReceived = false;

    public static boolean isIntegratedServer = false;

    /** Resolves the feature switches for this tick from the config and held feature keys. */
    public static void syncFeatures() {
        CrowBarConfig config = CrowBarConfigStore.config();
        nameTagsEnabled = FeatureKeys.isActive(config, CrowBarFeature.NAME_TAGS);
        skinsEnabled = FeatureKeys.isActive(config, CrowBarFeature.SKINS);
        viewSelfEnabled = FeatureKeys.isActive(config, CrowBarFeature.VIEW_SELF);
        showDistance = FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE);
    }

    // The claims the server sent, and the claim key's mode, live in the common ClaimWaypoints, so
    // every loader shares one copy of the mode handling GPExpansion talks to. Marker ids there only
    // matter from 1.21.6 on, where they suppress the duplicate vanilla waypoint.

    /** True once the server has sent claim data, which is how the claim key knows it is useful. */
    public static boolean hasClaimDataReceived() {
        return ClaimWaypoints.hasDataReceived();
    }

    public static boolean hasAnyClaimWaypoints() {
        return ClaimWaypoints.hasAny();
    }

    /** True only when the current claim mode has claims to replace player markers with. */
    public static boolean isClaimWaypointModeActive() {
        return ClaimWaypoints.isModeActive();
    }

    public static boolean shouldRenderPlayers() {
        return LocatorBarVisibility.shouldRenderPlayers(isClaimWaypointModeActive());
    }

    /** Claims to draw, already narrowed to the current mode. */
    public static java.util.List<ClaimWaypointData> getClaimWaypoints() {
        return ClaimWaypoints.selected();
    }

    public static boolean hasSelectedClaimWaypoints() {
        return ClaimWaypoints.hasSelected();
    }

    public static void clearClaimWaypoints() {
        ClaimWaypoints.reset();
    }

    public static void setExternalRenderSuppressed(String owner, boolean suppressed, boolean keepVanillaLocatorBar) {
        String key = owner == null || owner.isBlank() ? "external" : owner;
        if (suppressed) {
            externalRenderSuppressions.put(key, keepVanillaLocatorBar);
        } else {
            externalRenderSuppressions.remove(key);
        }
    }

    public static boolean isExternalRenderSuppressed() {
        return !externalRenderSuppressions.isEmpty();
    }

    private CrowBarState() {
    }
}
