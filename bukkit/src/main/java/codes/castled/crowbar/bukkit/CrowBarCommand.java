package codes.castled.crowbar.bukkit;

import codes.castled.crowbar.bukkit.claims.ClaimBarMode;
import codes.castled.crowbar.bukkit.locator.ColorMode;
import codes.castled.crowbar.bukkit.locator.FakeMarkers;
import codes.castled.crowbar.bukkit.locator.PlayerLocatorModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@code /crowbar color <#RRGGBB|reset> [player]}, {@code /crowbar claims <own|trusted|off>},
 * {@code /crowbar fake [count|clear]} and {@code /crowbar reload}.
 */
@SuppressWarnings("deprecation")
final class CrowBarCommand implements TabExecutor {
    private final CrowBarPlugin plugin;

    CrowBarCommand(CrowBarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " color <#RRGGBB|reset> [player]"
                    + ChatColor.GRAY + " - your dot color on CrowBar locator bars");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " claims <own|trusted|off>"
                    + ChatColor.GRAY + " - which claims show on your vanilla locator bar");
            if (sender.hasPermission("crowbar.fake")) {
                sender.sendMessage(ChatColor.YELLOW + "/" + label + " fake [count|clear]"
                        + ChatColor.GRAY + " - pretend players on your CrowBar bar, for screenshots and testing");
            }
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - reload the config");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "color", "colour" -> color(sender, args);
            case "claims" -> claims(sender, args);
            case "fake" -> fake(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /" + label + ".");
                yield true;
            }
        };
    }

    private boolean color(CommandSender sender, String[] args) {
        if (!sender.hasPermission("crowbar.color")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to choose a dot color.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /crowbar color <#RRGGBB|reset> [player]");
            return true;
        }

        OfflinePlayer target;
        if (args.length >= 3) {
            if (!sender.hasPermission("crowbar.color.others")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to set other players' colors.");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[2]);
            target = online != null ? online : Bukkit.getOfflinePlayer(args[2]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + args[2] + " has never joined this server.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(ChatColor.RED + "Name a player: /crowbar color <#RRGGBB|reset> <player>");
            return true;
        }

        Integer rgb = null;
        if (!args[1].equalsIgnoreCase("reset")) {
            rgb = PluginSettings.parseColor(args[1]);
            if (rgb == null) {
                sender.sendMessage(ChatColor.RED + args[1] + " is not a color. Use #RRGGBB, for example #55FFFF.");
                return true;
            }
        }
        plugin.colorStore().set(target.getUniqueId(), rgb);

        String whose = target.equals(sender) ? "Your" : target.getName() + "'s";
        sender.sendMessage(ChatColor.GREEN + whose + " dot color is now "
                + (rgb == null ? "the default" : PluginSettings.formatColor(rgb)) + ".");
        if (plugin.settings().colors().mode() != ColorMode.CUSTOM) {
            sender.sendMessage(ChatColor.GRAY + "It takes effect when colors.mode is custom; it is currently "
                    + plugin.settings().colors().mode().name().toLowerCase(Locale.ROOT) + ".");
        }
        return true;
    }

    private boolean claims(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a locator bar.");
            return true;
        }
        if (!sender.hasPermission("crowbar.claims")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to change your claim markers.");
            return true;
        }
        if (!plugin.claimsActive()) {
            sender.sendMessage(ChatColor.RED + "Claim markers are not active on this server.");
            return true;
        }
        ClaimBarMode mode = args.length >= 2 ? ClaimBarMode.parse(args[1]) : null;
        if (mode == null) {
            sender.sendMessage(ChatColor.YELLOW + "Your claim markers: "
                    + plugin.claimModes().get(player.getUniqueId()).name().toLowerCase(Locale.ROOT)
                    + ChatColor.GRAY + ". Use /crowbar claims <own|trusted|off>.");
            return true;
        }
        plugin.claimModes().set(player.getUniqueId(), mode);
        plugin.refreshClaims(player);
        sender.sendMessage(ChatColor.GREEN + "Your vanilla locator bar now shows " + switch (mode) {
            case OWN -> "claims you own.";
            case TRUSTED -> "claims you own or are trusted on.";
            case OFF -> "no claims.";
        });
        sender.sendMessage(ChatColor.GRAY + "With CrowBar installed, use its claim waypoint key instead.");
        return true;
    }

    private boolean fake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("crowbar.fake")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to make fake players.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a locator bar.");
            return true;
        }
        FakeMarkers fakes = plugin.fakes();
        PlayerLocatorModule locator = plugin.locator();
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            boolean had = fakes.clear(player.getUniqueId());
            if (locator != null) locator.refresh(player);
            sender.sendMessage(had ? ChatColor.GREEN + "Removed your fake players." : ChatColor.GRAY + "You have no fake players.");
            return true;
        }
        if (locator == null) {
            sender.sendMessage(ChatColor.RED + "CrowBar's player locator is disabled. Enable player-locator.enabled to use fake markers.");
            return true;
        }
        if (!PlayerLocatorModule.isListening(player)) {
            sender.sendMessage(ChatColor.RED + "Fake players only show on CrowBar's bar, and your client is not running CrowBar.");
            return true;
        }
        if (!player.hasPermission(PlayerLocatorModule.RECEIVE_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to receive CrowBar locator markers.");
            return true;
        }

        int count = 6;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                count = -1;
            }
            if (count < 1 || count > FakeMarkers.MAX_COUNT) {
                sender.sendMessage(ChatColor.RED + "Usage: /crowbar fake [1-" + FakeMarkers.MAX_COUNT + "|clear]");
                return true;
            }
        }
        var at = player.getLocation();
        fakes.spawn(player.getUniqueId(), player.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), count,
                plugin.settings().locator().maxDistance(), ThreadLocalRandom.current());
        locator.refresh(player);
        sender.sendMessage(ChatColor.GREEN + "Added " + count + " fake player" + (count == 1 ? "" : "s")
                + " around you. Only you can see them.");
        sender.sendMessage(ChatColor.GRAY + "They stay where they are until you leave this world. /crowbar fake clear removes them.");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("crowbar.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload CrowBar.");
            return true;
        }
        plugin.reload();
        sender.sendMessage(ChatColor.GREEN + "CrowBar reloaded.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("crowbar.color")) options.add("color");
            if (sender.hasPermission("crowbar.claims")) options.add("claims");
            if (sender.hasPermission("crowbar.fake")) options.add("fake");
            if (sender.hasPermission("crowbar.reload")) options.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            options.add("reset");
            options.add("#");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("claims")) {
            options.addAll(ClaimBarMode.ARGUMENTS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("fake") && sender.hasPermission("crowbar.fake")) {
            options.addAll(List.of("clear", "6", "12"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("color") && sender.hasPermission("crowbar.color.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) options.add(player.getName());
        } else {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
