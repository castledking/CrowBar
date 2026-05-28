package codes.castled.crowbar;

import java.util.UUID;

public final class AlliumPlayerData {
    public final UUID uuid;
    public final double x;
    public final double y;
    public final double z;
    public final boolean wearingPumpkin;
    public final boolean sneaking;
    public final boolean vanished;
    public final int teamColor; // RGB without alpha
    public final long timestamp;

    public AlliumPlayerData(UUID uuid, double x, double y, double z, boolean wearingPumpkin, boolean sneaking, boolean vanished, int teamColor) {
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.wearingPumpkin = wearingPumpkin;
        this.sneaking = sneaking;
        this.vanished = vanished;
        this.teamColor = teamColor;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired() {
        // Data expires after 5 seconds
        return System.currentTimeMillis() - timestamp > 5000;
    }
}
