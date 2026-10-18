package codes.castled.crowbar.bukkit;

import codes.castled.crowbar.bukkit.claims.ClaimBarModeStore;
import codes.castled.crowbar.bukkit.claims.ClaimWaypointModule;
import codes.castled.crowbar.bukkit.client.ClientSettingsModule;
import codes.castled.crowbar.bukkit.locator.FakeMarkers;
import codes.castled.crowbar.bukkit.locator.PlayerColorStore;
import codes.castled.crowbar.bukkit.locator.PlayerLocatorModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * The CrowBar server plugin.
 *
 * <p>Ships inside every CrowBar client jar, which makes each one a Bukkit plugin as well as a
 * Fabric or NeoForge mod. It runs three modules:
 * <ul>
 *   <li>the player locator, which sends CrowBar clients other players' positions, ported from
 *       Allium with Player Locator Plus's server settings;
 *   <li>claim waypoints, GPExpansion's bowtie claim markers for servers with GriefPrevention;
 *   <li>client settings, which pushes appearance settings to CrowBar clients and takes the dot
 *       colour players pick in CrowBar.
 * </ul>
 * The first two step aside when Allium or GPExpansion already provides the same thing.
 */
public final class CrowBarPlugin extends JavaPlugin {
    private PluginSettings settings;
    private PlayerColorStore colorStore;
    private ClaimBarModeStore claimModes;
    private final FakeMarkers fakes = new FakeMarkers();
    private PlayerLocatorModule locator;
    private volatile ClientSettingsModule clientSettings;
    /** Typed as Object so GriefPrevention's classes only load when it is installed. */
    private Object claims;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        colorStore = new PlayerColorStore(this, new File(getDataFolder(), "colors.yml"), getLogger());
        claimModes = new ClaimBarModeStore(new File(getDataFolder(), "claim-modes.yml"), getLogger());
        getServer().getMessenger().registerOutgoingPluginChannel(this, PlayerLocatorModule.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ClaimWaypointModule.CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ClientSettingsModule.SETTINGS_CHANNEL);
        // Registered once for the plugin's lifetime: Paper only tells clients which channels it
        // listens on as they join, so a channel registered on reload would go unannounced.
        getServer().getMessenger().registerIncomingPluginChannel(this, ClientSettingsModule.COLOR_CHANNEL, (channel, player, message) -> {
            if (clientSettings != null) clientSettings.onColorMessage(player, message);
        });

        CrowBarCommand command = new CrowBarCommand(this);
        PluginCommand registered = getCommand("crowbar");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
        startModules();
    }

    @Override
    public void onDisable() {
        stopModules();
        fakes.clearAll();
        colorStore.flush();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    /** Re-reads the config and restarts both modules. */
    public void reload() {
        reloadConfig();
        stopModules();
        startModules();
    }

    private void startModules() {
        settings = PluginSettings.from(getConfig(), getLogger());

        if (settings.locator().enabled()) {
            if (settings.locator().deferToAllium() && isPluginEnabled("Allium")) {
                getLogger().info("Allium is sending CrowBar player data, so the player locator stays off. "
                        + "Set player-locator.defer-to-allium to false to run it anyway.");
            } else {
                locator = new PlayerLocatorModule(this, settings, colorStore, fakes);
                locator.start();
                getLogger().info("Sending player positions to CrowBar clients every "
                        + settings.locator().updateIntervalTicks() + " tick(s).");
            }
        }

        if (settings.claims().enabled() && isPluginEnabled("GriefPrevention")) {
            if (isPluginEnabled("GPExpansion")) {
                getLogger().info("GPExpansion publishes claim waypoints, so CrowBar's claim markers stay off.");
            } else {
                ClaimWaypointModule module = new ClaimWaypointModule(this, settings.claims(), claimModes);
                module.start();
                claims = module;
                getLogger().info("Publishing GriefPrevention claims as locator bar waypoints"
                        + (module.vanillaMarkersActive() ? ", with vanilla markers for players without CrowBar." : "."));
            }
        }

        clientSettings = new ClientSettingsModule(this, settings, colorStore, locator != null);
        clientSettings.start();
        if (!settings.appearance().settings().isEmpty()) {
            getLogger().info("Choosing client-appearance " + String.join(", ", settings.appearance().configKeys())
                    + " for CrowBar clients.");
        }
    }

    private void stopModules() {
        if (clientSettings != null) {
            clientSettings.stop();
            clientSettings = null;
        }
        if (locator != null) {
            locator.stop();
            locator = null;
        }
        if (claims != null) {
            ((ClaimWaypointModule) claims).stop();
            claims = null;
        }
    }

    private boolean isPluginEnabled(String name) {
        return getServer().getPluginManager().isPluginEnabled(name);
    }

    PluginSettings settings() {
        return settings;
    }

    PlayerColorStore colorStore() {
        return colorStore;
    }

    ClaimBarModeStore claimModes() {
        return claimModes;
    }

    FakeMarkers fakes() {
        return fakes;
    }

    /** The running player locator, or {@code null} while it is off or Allium handles positions. */
    PlayerLocatorModule locator() {
        return locator;
    }

    boolean claimsActive() {
        return claims != null;
    }

    /** Re-sends claim markers to one player after their mode changes. */
    void refreshClaims(org.bukkit.entity.Player player) {
        if (claims != null) {
            ((ClaimWaypointModule) claims).rebuildViewer(player);
        }
    }
}
