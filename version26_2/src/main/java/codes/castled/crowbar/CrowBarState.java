package codes.castled.crowbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

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

    public static final Map<UUID, AlliumPlayerData> alliumPlayerData = new ConcurrentHashMap<>();
    private static boolean alliumDataReceived = false;
    public static boolean isIntegratedServer = false;

    private static int lastExperienceLevel = -1;
    private static float lastExperienceProgress = -1f;
    private static long xpGainShowUntil = 0;

    public static void markAlliumDataReceived() {
        alliumDataReceived = true;
    }

    public static boolean hasAlliumDataReceived() {
        return alliumDataReceived;
    }

    public static void clearAlliumData() {
        alliumPlayerData.clear();
        alliumDataReceived = false;
        isIntegratedServer = false;
    }

    public static boolean shouldUseClientPlayerFallback() {
        return isIntegratedServer && !alliumDataReceived;
    }

    public static boolean isVanillaLocatorBarVisible() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        return connection != null && connection.getWaypointManager().hasWaypoints();
    }

    public static Optional<Vec3> getPlayerPos(UUID uuid) {
        AlliumPlayerData allium = alliumPlayerData.get(uuid);
        if (allium != null && !allium.isExpired()) {
            return Optional.of(new Vec3(allium.x, allium.y, allium.z));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            if (serverPlayer != null) {
                return Optional.of(new Vec3(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()));
            }
        }

        if (mc.level != null) {
            Player player = mc.level.getPlayerInAnyDimension(uuid);
            if (player != null) {
                return Optional.of(new Vec3(player.getX(), player.getY(), player.getZ()));
            }
        }

        return Optional.empty();
    }

    public static boolean hasRenderablePlayers(UUID selfUuid) {
        if (selfUuid == null) return false;
        for (AlliumPlayerData data : alliumPlayerData.values()) {
            if (data.isExpired()) continue;
            if (!data.uuid.equals(selfUuid)) return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            for (ServerPlayer player : mc.getSingleplayerServer().getPlayerList().getPlayers()) {
                if (!player.getUUID().equals(selfUuid)) return true;
            }
        }

        if (!alliumDataReceived && mc.level != null) {
            for (var player : mc.level.players()) {
                if (!player.getUUID().equals(selfUuid)) return true;
            }
        }

        return false;
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
        if (player.gameMode().isSurvival() == false) return;

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
