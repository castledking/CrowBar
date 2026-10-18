package codes.castled.crowbar.bukkit.locator;

import java.util.UUID;

/**
 * A stable, saturated colour per player.
 *
 * <p>The same golden-angle formula the CrowBar client and Allium use, so a player gets the same
 * hue whichever side picks it. Kept as a copy because the plugin cannot see the client's classes
 * inside a Fabric jar.
 */
public final class UniqueColor {
    private static final float GOLDEN_ANGLE = 137.508f;

    private UniqueColor() {
    }

    /** RGB without alpha. */
    public static int of(UUID uuid) {
        long hash = uuid.hashCode() & 0xffffffffL;
        float hue = (hash * GOLDEN_ANGLE) % 360f;
        float saturation = 0.75f + ((hash >> 8) & 0xFF) / 255f * 0.20f;
        float brightness = 0.85f + ((hash >> 16) & 0xFF) / 255f * 0.15f;
        return java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness) & 0xFFFFFF;
    }
}
