package codes.castled.crowbar.bukkit;

import codes.castled.crowbar.bukkit.locator.ColorMode;
import codes.castled.crowbar.bukkit.locator.LocatorPrecision;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/** The plugin's config, read once per (re)load. Invalid values fall back to their defaults with a warning. */
public record PluginSettings(Locator locator, Hiding hiding, LocatorPrecision precision, Colors colors, Claims claims, Appearance appearance) {

    public record Locator(boolean enabled, boolean deferToAllium, int updateIntervalTicks, double maxDistance) {
    }

    public record Hiding(boolean sneaking, boolean invisible, boolean spectating, boolean vanished, Set<Material> headItems) {
    }

    /**
     * @param constantColor RGB used by {@link ColorMode#CONSTANT}
     * @param players       RGB overrides keyed by lower-case player name
     */
    public record Colors(ColorMode mode, int constantColor, Map<String, Integer> players) {
    }

    public record Claims(boolean enabled, boolean vanillaMarkers, int vanillaMarkerRange, boolean subdivisions, boolean adminClaims) {
    }

    /**
     * Appearance settings pushed to CrowBar clients, keyed by the client's own setting name and
     * holding JSON-ready values. Settings left to each player are absent.
     */
    public record Appearance(Map<String, Object> settings) {
        /** The {@code client-appearance} keys that are set, for log messages. */
        public java.util.List<String> configKeys() {
            return APPEARANCE_KEYS.entrySet().stream()
                    .filter(key -> settings.containsKey(key.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }
    }

    /** {@code client-appearance} keys and the client setting each one sets. */
    private static final Map<String, String> APPEARANCE_KEYS = Map.ofEntries(
            Map.entry("label-background", "labelBackground"),
            Map.entry("raise-hud", "raiseHud"),
            Map.entry("dim-other-labels", "dimUnfocusedLabels"),
            Map.entry("hide-overlapping-labels", "hideOverlappingLabels"),
            Map.entry("skin-frame", "skinBorder"),
            Map.entry("height-arrows", "heightArrows"),
            Map.entry("fade-distant-markers", "fadeDistantMarkers"),
            Map.entry("fade-start", "fadeStart"),
            Map.entry("fade-end", "fadeEnd"),
            Map.entry("fade-end-opacity", "fadeEndOpacity"),
            Map.entry("dot-colors", "dotColorMode"),
            Map.entry("dot-color", "customDotColor"));

    public static PluginSettings from(FileConfiguration config, Logger logger) {
        Locator locator = new Locator(
                config.getBoolean("player-locator.enabled", true),
                config.getBoolean("player-locator.defer-to-allium", true),
                Math.max(1, config.getInt("player-locator.update-interval-ticks", 2)),
                Math.max(0, config.getDouble("player-locator.max-distance", 0)));

        Set<Material> headItems = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("hide-when.head-items")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                logger.warning("hide-when.head-items: unknown item " + name + ", ignoring it");
            } else {
                headItems.add(material);
            }
        }
        Hiding hiding = new Hiding(
                config.getBoolean("hide-when.sneaking", true),
                config.getBoolean("hide-when.invisible", true),
                config.getBoolean("hide-when.spectating", true),
                config.getBoolean("hide-when.vanished", true),
                headItems);

        LocatorPrecision precision = new LocatorPrecision(
                Math.max(0, config.getInt("precision.direction-steps", 0)),
                config.getBoolean("precision.send-distance", true),
                Math.max(0, config.getDouble("precision.round-distance-beyond", 200)),
                Math.max(0, config.getDouble("precision.round-distance-to", 0)));

        ColorMode mode = ColorMode.parse(config.getString("colors.mode", "team"));
        if (mode == null) {
            logger.warning("colors.mode: unknown mode " + config.getString("colors.mode") + ", using team");
            mode = ColorMode.TEAM;
        }
        Integer constant = parseColor(config.getString("colors.constant-color", "#FFFFFF"));
        if (constant == null) {
            logger.warning("colors.constant-color: not a #RRGGBB color, using white");
            constant = 0xFFFFFF;
        }
        Map<String, Integer> players = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("colors.players");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                Integer rgb = parseColor(section.getString(name));
                if (rgb == null) {
                    logger.warning("colors.players." + name + ": not a #RRGGBB color, ignoring it");
                } else {
                    players.put(name.toLowerCase(Locale.ROOT), rgb);
                }
            }
        }
        Colors colors = new Colors(mode, constant, Map.copyOf(players));

        Claims claims = new Claims(
                config.getBoolean("claim-waypoints.enabled", true),
                config.getBoolean("claim-waypoints.vanilla-markers", true),
                Math.max(0, config.getInt("claim-waypoints.vanilla-marker-range", 1000)),
                config.getBoolean("claim-waypoints.subdivisions", false),
                config.getBoolean("claim-waypoints.admin-claims", false));

        return new PluginSettings(locator, hiding, precision, colors, claims, appearance(config, logger));
    }

    private static Appearance appearance(FileConfiguration config, Logger logger) {
        Map<String, Object> settings = new TreeMap<>();
        for (Map.Entry<String, String> key : APPEARANCE_KEYS.entrySet()) {
            String path = "client-appearance." + key.getKey();
            Object raw = config.get(path);
            if (raw == null || (raw instanceof String text && text.trim().equalsIgnoreCase("player"))) continue;
            Object value = appearanceValue(key.getValue(), raw);
            if (value == null) {
                logger.warning(path + ": " + raw + " is not a valid value, leaving it to each player");
            } else {
                settings.put(key.getValue(), value);
            }
        }
        return new Appearance(Collections.unmodifiableMap(settings));
    }

    /** The JSON value for one client setting, or {@code null} if {@code raw} does not fit it. */
    private static Object appearanceValue(String setting, Object raw) {
        switch (setting) {
            case "fadeStart", "fadeEnd" -> {
                return raw instanceof Number number && number.intValue() >= 0 ? number.intValue() : null;
            }
            case "fadeEndOpacity" -> {
                if (!(raw instanceof Number number)) return null;
                double opacity = number.doubleValue();
                return opacity >= 0 && opacity <= 1 ? opacity : null;
            }
            case "dotColorMode" -> {
                // The names the CrowBar settings screen uses, plus the client's own.
                return switch (String.valueOf(raw).trim().toLowerCase(Locale.ROOT)) {
                    case "team" -> "team";
                    case "per-player", "unique" -> "unique";
                    case "single", "custom" -> "custom";
                    default -> null;
                };
            }
            case "customDotColor" -> {
                Integer rgb = parseColor(String.valueOf(raw).trim());
                return rgb != null ? formatColor(rgb) : null;
            }
            default -> {
                return raw instanceof Boolean flag ? flag : null;
            }
        }
    }

    /** Parses {@code #RRGGBB} or {@code RRGGBB}; returns RGB, or {@code null} if malformed. */
    public static Integer parseColor(String value) {
        if (value == null) return null;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }
}
