package codes.castled.crowbar.bukkit.locator;

import java.util.Locale;

/** How the server colours each player's dot. Mirrors Player Locator Plus's colour modes. */
public enum ColorMode {
    /** Scoreboard team colour, otherwise a colour unique to the player. */
    TEAM,
    /** A colour unique to each player. */
    UNIQUE,
    /** One colour for everyone. */
    CONSTANT,
    /** Each player's own choice from {@code /crowbar color}, otherwise unique. */
    CUSTOM;

    /** @return the mode, or {@code null} for an unknown name */
    public static ColorMode parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
