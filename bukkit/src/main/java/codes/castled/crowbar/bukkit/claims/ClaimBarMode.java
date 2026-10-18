package codes.castled.crowbar.bukkit.claims;

import java.util.List;
import java.util.Locale;

/**
 * Which claims appear on a player's vanilla locator bar. Ported from GPExpansion.
 *
 * <p>Only narrows the vanilla marker packets. CrowBar clients receive every claim they may see and
 * pick what to show with their own claim waypoint key.
 */
public enum ClaimBarMode {
    /** Only claims the player owns. */
    OWN,
    /** Claims the player owns or is trusted on. */
    TRUSTED,
    /** Nothing appears on the vanilla bar. */
    OFF;

    /** The values a player can type, in a stable order. */
    public static final List<String> ARGUMENTS = List.of("own", "trusted", "off");

    /** @return the mode, or {@code null} when the argument is not a known mode */
    public static ClaimBarMode parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
