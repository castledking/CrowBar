package codes.castled.crowbar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrowBarState {
    private static final long XP_BAR_VISIBLE_DURATION_MS = 5000;

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
    /**
     * Marker entities backing those claims. Their vanilla waypoints are suppressed so a claim is
     * not drawn twice when the server also spawns marker entities for unmodified clients.
     */
    private static volatile java.util.Set<UUID> claimMarkerIds = java.util.Set.of();

    /** Which claims the locator bar shows. Cycled by the claim waypoint keybind. */
    public enum ClaimWaypointMode { OFF, OWNED, TRUSTED }

    public static volatile ClaimWaypointMode claimWaypointMode = ClaimWaypointMode.TRUSTED;
    private static volatile boolean claimDataReceived = false;

    /** True once GPExpansion has sent anything, which is how the keybind knows it is useful. */
    public static boolean hasClaimDataReceived() {
        return claimDataReceived;
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

    public static void setClaimWaypoints(java.util.List<ClaimWaypointData> claims, java.util.Set<UUID> markerIds) {
        claimDataReceived = true;
        claimWaypoints = java.util.List.copyOf(claims);
        claimMarkerIds = java.util.Set.copyOf(markerIds);
    }

    /** Claims to draw, already filtered by the current mode. */
    public static java.util.List<ClaimWaypointData> getClaimWaypoints() {
        return switch (claimWaypointMode) {
            case OFF -> java.util.List.of();
            case OWNED -> claimWaypoints.stream().filter(ClaimWaypointData::owned).toList();
            case TRUSTED -> claimWaypoints;
        };
    }

    public static boolean isClaimMarker(UUID uuid) {
        return uuid != null && claimMarkerIds.contains(uuid);
    }

    public static void clearClaimWaypoints() {
        claimWaypoints = java.util.List.of();
        claimMarkerIds = java.util.Set.of();
    }
    public static boolean isIntegratedServer = false;

    private static int lastExperienceLevel = -1;
    private static float lastExperienceProgress = -1f;
    private static long xpGainShowUntil = 0;

    /**
     * Get player position from the best available source:
     * 1. Allium plugin data (Paper server) - unlimited range
     * 2. IntegratedServer direct access (LAN/singleplayer) - unlimited range
     * 3. Client entity lookup (close range fallback) - render distance limited
     */
    public static Optional<Vec3d> getPlayerPos(UUID uuid) {
        // 1. Try Allium data first (Paper server)
        AlliumPlayerData allium = alliumPlayerData.get(uuid);
        if (allium != null && !allium.isExpired()) {
            return Optional.of(new Vec3d(allium.x, allium.y, allium.z));
        }

        // 2. Try IntegratedServer direct access (LAN/singleplayer)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() != null) {
            ServerPlayerEntity serverPlayer = mc.getServer().getPlayerManager().getPlayer(uuid);
            if (serverPlayer != null) {
                return Optional.of(new Vec3d(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()));
            }
        }

        // 3. Try loaded client entity (close range fallback)
        if (mc.world != null) {
            for (var player : mc.world.getPlayers()) {
                if (player.getUuid().equals(uuid)) {
                    return Optional.of(new Vec3d(player.getX(), player.getY(), player.getZ()));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Check if there are any Allium entries worth rendering (non-expired, not self).
     */
    public static boolean hasRenderableAlliumEntries(UUID selfUuid) {
        if (selfUuid == null) return false;
        for (AlliumPlayerData data : alliumPlayerData.values()) {
            if (data.isExpired()) continue;
            if (data.uuid.equals(selfUuid)) continue;
            return true;
        }
        return false;
    }

    public static boolean isVanillaLocatorBarVisible() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return false;
        try {
            java.lang.reflect.Field f = handler.getClass().getDeclaredField("waypointManager");
            f.setAccessible(true);
            Object manager = f.get(handler);
            java.lang.reflect.Method m = manager.getClass().getMethod("hasWaypoints");
            return (Boolean) m.invoke(manager);
        } catch (Exception e) {
            return false;
        }
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

    public static boolean shouldKeepVanillaLocatorBarDuringExternalSuppression() {
        if (externalRenderSuppressions.isEmpty()) return true;
        return externalRenderSuppressions.values().stream().allMatch(Boolean::booleanValue);
    }

    public static void checkXpGain(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) return;

        int currentLevel = player.experienceLevel;
        float currentProgress = player.experienceProgress;

        if (lastExperienceLevel >= 0 && lastExperienceProgress >= 0) {
            if (currentLevel != lastExperienceLevel || currentProgress > lastExperienceProgress) {
                xpGainShowUntil = System.currentTimeMillis() + XP_BAR_VISIBLE_DURATION_MS;
            }
        }

        lastExperienceLevel = currentLevel;
        lastExperienceProgress = currentProgress;
    }

    public static boolean isXpBarVisible() {
        return System.currentTimeMillis() < xpGainShowUntil;
    }

    private CrowBarState() {
    }
}
