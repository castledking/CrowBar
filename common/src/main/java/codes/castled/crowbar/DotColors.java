package codes.castled.crowbar;

import java.util.Locale;
import java.util.UUID;

/** Dot colour rules shared by every renderer. All colours returned are opaque ARGB. */
public final class DotColors {
    // Golden angle (≈137.508°), matching Allium's server-side fallback so a player gets the same
    // hue from the server and from the client fallback.
    private static final float GOLDEN_ANGLE = 137.508f;

    /** Colour choices offered by the config screen, in cycling order. */
    public enum Preset {
        WHITE(0xFFFFFF),
        RED(0xFF5555),
        GOLD(0xFFAA00),
        YELLOW(0xFFFF55),
        GREEN(0x55FF55),
        DARK_GREEN(0x00AA00),
        AQUA(0x55FFFF),
        DARK_AQUA(0x00AAAA),
        BLUE(0x5555FF),
        LIGHT_PURPLE(0xFF55FF),
        DARK_PURPLE(0xAA00AA),
        GRAY(0xAAAAAA);

        public final int rgb;

        Preset(int rgb) {
            this.rgb = rgb;
        }

        public String translationKey() {
            return "crowbar.color." + name().toLowerCase(Locale.ROOT);
        }

        public static Preset of(int rgb) {
            for (Preset preset : values()) {
                if (preset.rgb == (rgb & 0xFFFFFF)) return preset;
            }
            return null;
        }
    }

    private DotColors() {
    }

    /**
     * Colour of another player's dot.
     *
     * @param teamColor ARGB team colour from Allium or the scoreboard, or {@code null} when the
     *                  player has no team colour
     */
    public static int forPlayer(CrowBarConfig config, UUID uuid, Integer teamColor) {
        return switch (config.dotColorMode) {
            case TEAM -> teamColor != null ? opaque(teamColor) : generated(uuid);
            case UNIQUE -> generated(uuid);
            case CUSTOM -> opaque(parseHex(config.customDotColor, 0xFFFFFF));
        };
    }

    /** Colour of your own dot in view-self mode. */
    public static int forSelf(CrowBarConfig config, UUID uuid, Integer teamColor) {
        if (!CrowBarConfig.AUTO_COLOR.equals(config.selfDotColor)) {
            Integer chosen = parseHex(config.selfDotColor);
            if (chosen != null) return opaque(chosen);
        }
        return forPlayer(config, uuid, teamColor);
    }

    /** A stable, saturated colour for a player without a team colour. */
    public static int generated(UUID uuid) {
        long hash = uuid.hashCode() & 0xffffffffL;
        float hue = (hash * GOLDEN_ANGLE) % 360f;
        float saturation = 0.75f + ((hash >> 8) & 0xFF) / 255f * 0.20f;
        float brightness = 0.85f + ((hash >> 16) & 0xFF) / 255f * 0.15f;
        return opaque(java.awt.Color.HSBtoRGB(hue / 360f, saturation, brightness));
    }

    /**
     * Vanilla's colour for a waypoint without a set colour: the id's hash at 90% brightness.
     *
     * <p>Minecraft only gained the helper for this in 1.21.6, so the older jars use this copy.
     */
    public static int fromHash(int hash) {
        float[] hsb = java.awt.Color.RGBtoHSB((hash >> 16) & 0xFF, (hash >> 8) & 0xFF, hash & 0xFF, null);
        return opaque(java.awt.Color.HSBtoRGB(hsb[0], hsb[1], 0.9F));
    }

    /** Scales the alpha channel of {@code argb} by {@code alpha / 255}. */
    public static int withAlpha(int argb, int alpha) {
        int scaled = ((argb >>> 24) * Math.max(0, Math.min(255, alpha)) + 127) / 255;
        return (scaled << 24) | (argb & 0xFFFFFF);
    }

    /** Parses {@code #RRGGBB} or {@code RRGGBB}; returns RGB or {@code null} if malformed. */
    public static Integer parseHex(String value) {
        if (value == null) return null;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int parseHex(String value, int fallback) {
        Integer parsed = parseHex(value);
        return parsed != null ? parsed : fallback;
    }

    public static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
