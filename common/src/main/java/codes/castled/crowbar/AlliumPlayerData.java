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
    /**
     * The position only gives a direction. Servers that withhold distances (the CrowBar plugin's
     * {@code precision.send-distance: false}) place the player at a nominal distance, which must
     * not be shown or used for sizing.
     */
    public final boolean bearingOnly;
    /**
     * The name to show when the player is not in the tab list, such as the CrowBar plugin's fake
     * markers, or {@code null} to look the name up from the tab list as usual.
     */
    public final String name;
    /**
     * Whether the entry lapses without a fresh copy. Allium and the CrowBar plugin resend every
     * player constantly, so their entries expire; Player Locator Plus only sends changes, so its
     * entries stay until the server removes them.
     */
    public final boolean expires;
    /** Private screenshot markers arrive separately from real player snapshots. */
    public final boolean fake;
    public final long timestamp;

    public AlliumPlayerData(UUID uuid, double x, double y, double z, boolean wearingPumpkin, boolean sneaking, boolean vanished, int teamColor) {
        this(uuid, x, y, z, wearingPumpkin, sneaking, vanished, teamColor, false);
    }

    public AlliumPlayerData(UUID uuid, double x, double y, double z, boolean wearingPumpkin, boolean sneaking, boolean vanished, int teamColor, boolean bearingOnly) {
        this(uuid, x, y, z, wearingPumpkin, sneaking, vanished, teamColor, bearingOnly, null, true);
    }

    public AlliumPlayerData(UUID uuid, double x, double y, double z, boolean wearingPumpkin, boolean sneaking, boolean vanished, int teamColor, boolean bearingOnly, String name, boolean expires) {
        this(uuid, x, y, z, wearingPumpkin, sneaking, vanished, teamColor, bearingOnly, name, expires, false);
    }

    public AlliumPlayerData(UUID uuid, double x, double y, double z, boolean wearingPumpkin, boolean sneaking, boolean vanished, int teamColor, boolean bearingOnly, String name, boolean expires, boolean fake) {
        this.bearingOnly = bearingOnly;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.wearingPumpkin = wearingPumpkin;
        this.sneaking = sneaking;
        this.vanished = vanished;
        this.teamColor = teamColor;
        this.name = name;
        this.expires = expires;
        this.fake = fake;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired() {
        // Data expires after 5 seconds
        return expires && System.currentTimeMillis() - timestamp > 5000;
    }
}
