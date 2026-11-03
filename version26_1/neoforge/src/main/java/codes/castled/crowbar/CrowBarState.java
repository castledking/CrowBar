package codes.castled.crowbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrowBarState {
    private static final long XP_BAR_VISIBLE_DURATION_MS = 5000;

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

    // The claims the server sent, and the claim key's mode, live in the common ClaimWaypoints, so
    // every loader shares one copy of the mode handling GPExpansion talks to.

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

    /** Whether a vanilla waypoint is a claim drawn from claim data instead. */
    public static boolean isClaimMarker(UUID uuid) {
        return ClaimWaypoints.isMarker(uuid);
    }

    public static void clearClaimWaypoints() {
        ClaimWaypoints.reset();
    }
    public static boolean isIntegratedServer = false;

    /** Resolves the feature switches for this tick from the config and held feature keys. */
    public static void syncFeatures() {
        CrowBarConfig config = CrowBarConfigStore.config();
        nameTagsEnabled = FeatureKeys.isActive(config, CrowBarFeature.NAME_TAGS);
        skinsEnabled = FeatureKeys.isActive(config, CrowBarFeature.SKINS);
        viewSelfEnabled = FeatureKeys.isActive(config, CrowBarFeature.VIEW_SELF);
        showDistance = FeatureKeys.isActive(config, CrowBarFeature.SHOW_DISTANCE);
    }

    private static int lastExperienceLevel = -1;
    private static float lastExperienceProgress = -1f;
    private static long xpGainShowUntil = 0;

    /**
     * Get player position from the best available source:
     * 1. Allium plugin data (Paper server) - unlimited range
     * 2. IntegratedServer direct access (LAN/singleplayer) - unlimited range
     * 3. Client entity lookup (close range fallback) - render distance limited
     */
    public static Optional<Vec3> getPlayerPos(UUID uuid) {
        // 1. Try Allium data first (Paper server)
        AlliumPlayerData allium = alliumPlayerData.get(uuid);
        if (allium != null && !allium.isExpired()) {
            return Optional.of(new Vec3(allium.x, allium.y, allium.z));
        }

        // 2. Try IntegratedServer direct access (LAN/singleplayer)
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            if (serverPlayer != null) {
                return Optional.of(new Vec3(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()));
            }
        }

        // 3. Try loaded client entity (close range fallback)
        if (mc.level != null) {
            for (var player : mc.level.players()) {
                if (player.getUUID().equals(uuid)) {
                    return Optional.of(new Vec3(player.getX(), player.getY(), player.getZ()));
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
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener handler = mc.getConnection();
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

    /** Single visibility decision used by both halves of vanilla's locator renderer. */
    public static boolean shouldCancelVanillaLocatorBar() {
        Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
        boolean hasRenderablePlayers = cameraEntity != null
                && hasRenderableAlliumEntries(cameraEntity.getUUID());
        return LocatorBarVisibility.shouldCancelVanilla(
                isXpBarVisible(),
                isExternalRenderSuppressed(),
                shouldKeepVanillaLocatorBarDuringExternalSuppression(),
                viewSelfEnabled,
                alliumDataReceived,
                isClaimWaypointModeActive(),
                isIntegratedServer,
                hasRenderablePlayers);
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

    public static void checkXpGain(net.minecraft.world.entity.player.Player player) {
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
