package codes.castled.crowbar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrowBarState {
    public static boolean nameTagsEnabled = true;
    public static boolean skinsEnabled = true;
    public static boolean viewSelfEnabled = false;
    public static boolean forceShowAll = false;
    public static boolean showDistance = false;

    // Storage for Allium-provided player data
    public static final Map<UUID, AlliumPlayerData> alliumPlayerData = new ConcurrentHashMap<>();

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

    private CrowBarState() {
    }
}
