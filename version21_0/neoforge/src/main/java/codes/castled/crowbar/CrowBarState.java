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

    /** Claims this player may see, from GPExpansion. Empty without the server plugin. */
    private static volatile java.util.List<ClaimWaypointData> claimWaypoints = java.util.List.of();

    /** Which claims the locator bar shows. Cycled by the claim waypoint keybind. */
    public enum ClaimWaypointMode { OFF, OWNED, TRUSTED }

    public static volatile ClaimWaypointMode claimWaypointMode = ClaimWaypointMode.TRUSTED;
    private static volatile boolean claimDataReceived = false;

    public static boolean isIntegratedServer = false;

    /** Resolves the feature switches for this tick from the config and held feature keys. */
    public static void syncFeatures() {
        CrowBarConfig config = CrowBarConfigStore.config();
        nameTagsEnabled = FeatureKeys.isActive(config, CrowBarFeature.NAME_TAGS);
        skinsEnabled = FeatureKeys.isActive(config, CrowBarFeature.SKINS);
        viewSelfEnabled = FeatureKeys.isActive(config, CrowBarFeature.VIEW_SELF);
        showDistance = FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE);
    }

    /** True once GPExpansion has sent anything, which is how the keybind knows it is useful. */
    public static boolean hasClaimDataReceived() {
        return claimDataReceived;
    }

    public static boolean hasAnyClaimWaypoints() {
        return !claimWaypoints.isEmpty();
    }

    /** True only when the selected GPExpansion mode has claims to replace player mode with. */
    public static boolean isClaimWaypointModeActive() {
        return LocatorBarVisibility.isClaimWaypointModeActive(
                claimDataReceived,
                claimWaypointMode != ClaimWaypointMode.OFF,
                hasSelectedClaimWaypoints());
    }

    public static boolean shouldRenderPlayers() {
        return LocatorBarVisibility.shouldRenderPlayers(isClaimWaypointModeActive());
    }

    /** Advances OFF to OWNED to TRUSTED and back to OFF. */
    public static ClaimWaypointMode cycleClaimWaypointMode() {
        claimWaypointMode = switch (claimWaypointMode) {
            case OFF -> ClaimWaypointMode.OWNED;
            case OWNED -> ClaimWaypointMode.TRUSTED;
            case TRUSTED -> ClaimWaypointMode.OFF;
        };
        return claimWaypointMode;
    }

    /**
     * Stores the claims GPExpansion published. Marker entity ids only matter from 1.21.6 on, where
     * they suppress the duplicate vanilla waypoint; older versions have no waypoints to suppress.
     */
    public static void setClaimWaypoints(java.util.List<ClaimWaypointData> claims, java.util.Set<UUID> markerIds) {
        claimDataReceived = true;
        claimWaypoints = java.util.List.copyOf(claims);
    }

    /** Claims to draw, already filtered by the current mode. */
    public static java.util.List<ClaimWaypointData> getClaimWaypoints() {
        return switch (claimWaypointMode) {
            case OFF -> java.util.List.of();
            case OWNED -> claimWaypoints.stream().filter(ClaimWaypointData::owned).toList();
            case TRUSTED -> claimWaypoints;
        };
    }

    public static boolean hasSelectedClaimWaypoints() {
        return switch (claimWaypointMode) {
            case OFF -> false;
            case OWNED -> claimWaypoints.stream().anyMatch(ClaimWaypointData::owned);
            case TRUSTED -> !claimWaypoints.isEmpty();
        };
    }

    public static void clearClaimWaypoints() {
        claimWaypoints = java.util.List.of();
        claimDataReceived = false;
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
