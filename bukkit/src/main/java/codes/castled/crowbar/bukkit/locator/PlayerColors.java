package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.bukkit.PluginSettings;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Picks the dot colour a viewer is told to use for each player. */
public final class PlayerColors {
    private static final Map<ChatColor, Integer> LEGACY_RGB = new EnumMap<>(ChatColor.class);

    static {
        LEGACY_RGB.put(ChatColor.BLACK, 0x000000);
        LEGACY_RGB.put(ChatColor.DARK_BLUE, 0x0000AA);
        LEGACY_RGB.put(ChatColor.DARK_GREEN, 0x00AA00);
        LEGACY_RGB.put(ChatColor.DARK_AQUA, 0x00AAAA);
        LEGACY_RGB.put(ChatColor.DARK_RED, 0xAA0000);
        LEGACY_RGB.put(ChatColor.DARK_PURPLE, 0xAA00AA);
        LEGACY_RGB.put(ChatColor.GOLD, 0xFFAA00);
        LEGACY_RGB.put(ChatColor.GRAY, 0xAAAAAA);
        LEGACY_RGB.put(ChatColor.DARK_GRAY, 0x555555);
        LEGACY_RGB.put(ChatColor.BLUE, 0x5555FF);
        LEGACY_RGB.put(ChatColor.GREEN, 0x55FF55);
        LEGACY_RGB.put(ChatColor.AQUA, 0x55FFFF);
        LEGACY_RGB.put(ChatColor.RED, 0xFF5555);
        LEGACY_RGB.put(ChatColor.LIGHT_PURPLE, 0xFF55FF);
        LEGACY_RGB.put(ChatColor.YELLOW, 0xFFFF55);
    }

    private final PluginSettings.Colors settings;
    private final PlayerColorStore store;

    public PlayerColors(PluginSettings.Colors settings, PlayerColorStore store) {
        this.settings = settings;
        this.store = store;
    }

    /** RGB for {@code target}'s dot on {@code viewer}'s bar. */
    public int colorFor(Player viewer, Player target) {
        Integer override = settings.players().get(target.getName().toLowerCase(Locale.ROOT));
        if (override != null) return override;

        return switch (settings.mode()) {
            case TEAM -> {
                Integer team = teamColor(viewer, target);
                yield team != null ? team : UniqueColor.of(target.getUniqueId());
            }
            case UNIQUE -> UniqueColor.of(target.getUniqueId());
            case CONSTANT -> settings.constantColor();
            case CUSTOM -> {
                Integer chosen = store.get(target.getUniqueId());
                yield chosen != null ? chosen : UniqueColor.of(target.getUniqueId());
            }
        };
    }

    /**
     * RGB for a {@link FakeMarkers fake player}, which has no team, no saved choice and no name
     * that an override could be meant for.
     */
    public int colorForFake(UUID id) {
        return settings.mode() == ColorMode.CONSTANT ? settings.constantColor() : UniqueColor.of(id);
    }

    /**
     * The target's team colour as the viewer sees it, or {@code null} without one.
     *
     * <p>Read from the viewer's scoreboard, since plugins such as TAB give each player their own.
     * White counts as no colour, which is what a team without one reports.
     */
    @SuppressWarnings("deprecation")
    private static Integer teamColor(Player viewer, Player target) {
        try {
            Team team = viewer.getScoreboard().getEntryTeam(target.getName());
            if (team == null) return null;
            return LEGACY_RGB.get(team.getColor());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // A team with a non-legacy colour, or one unregistered mid-read.
            return null;
        }
    }
}
