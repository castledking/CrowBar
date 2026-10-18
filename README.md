[![CrowBar](https://castled.codes/assets/crowbar-banner.png)](https://github.com/castledking/CrowBar)

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/CrowBar/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/CrowBar/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

CrowBar is a client mod for Minecraft 1.21-1.21.11, 26.1.x, 26.2, and 26.3 that extends the locator bar with readable player name tags, skin markers, distance text, team-colored dots, and a self-view capture mode. On 1.21-1.21.5, which have no locator bar, it draws the same markers over the experience bar. Available for both Fabric and NeoForge.

## Features

- Renders player names above locator bar markers.
- Can replace locator dots with player skin faces.
- Shows optional distance text next to player names.
- Preserves team colors when a team color is available.
- Adds a self-view mode for clean locator bar screenshots.
- Hides hearts, hunger, armor, and the vanilla locator bar while self-view mode is active.
- Brings player markers to Minecraft 1.21-1.21.5 by drawing them over the experience bar.
- Settings screen on the `\` key, in Mod Menu on Fabric, and in the mod list on NeoForge.
- Every feature key can toggle or work only while held.
- Optional appearance settings: label backgrounds, a HUD that lifts out of the labels' way, overlap hiding, team-colored skin frames, dot colors, and distance fading.
- Works as a client-side mod: neither the server nor other players need to install CrowBar.
- Every jar is also an optional Paper, Spigot and Folia plugin that sends CrowBar players' positions and GriefPrevention claim markers. See [Server Plugin](#server-plugin).

## Minecraft 1.21-1.21.5

Minecraft added the locator bar in 1.21.6. On 1.21-1.21.5 CrowBar draws its markers over the experience bar instead, with the same names, faces, distances, colors and settings. In creative, where there is no experience bar, it draws a thin track for the markers to sit on.

Only you need CrowBar. Nobody else on the server has to install it, and neither does the server. What changes is where the player positions come from, since these versions have no locator bar for the server to fill:

- **Singleplayer and LAN hosts:** every player in your world, at any distance.
- **Servers running the [CrowBar plugin](#server-plugin) or [Allium](#allium-servers):** every player the server shares, at any distance.
- **Any other server:** only the players your client already has loaded. That ends at the server's player tracking range, 48 blocks by default on Paper and Spigot, so farther players do not appear.

Players hide from the bar the same way they do from vanilla's locator bar on 1.21.6 and later: by sneaking, being invisible, wearing a carved pumpkin or mob head, or spectating.

## Server Plugin

Every CrowBar jar is also a server plugin. Drop the jar for your server's Minecraft version into a Paper, Spigot, Purpur or Folia server's `plugins` folder; the same file that is a Fabric or NeoForge mod on a client is a Bukkit plugin on a server. `CrowBar-plugin-<version>.jar` on the GitHub release is the plugin on its own.

The plugin is optional, and players without CrowBar are unaffected by it. It has two parts.

**Player locator.** Sends CrowBar players the positions of everyone in their world, so the bar shows players far beyond the server's tracking range. That matters most on 1.21-1.21.5, where CrowBar otherwise only sees nearby players. Hidden players are left out of the data entirely, so their position never reaches anyone's client. It brings over the server settings from [Player Locator Plus](https://github.com/timas130/PlayerLocatorPlus):

| Setting | Default | What it does |
| --- | --- | --- |
| `player-locator.update-interval-ticks` | `2` | Ticks between updates. |
| `player-locator.max-distance` | `0` | Farthest a player can be and still appear. `0` is unlimited. |
| `hide-when.sneaking` / `invisible` / `spectating` / `vanished` | `true` | Who stays off the bar, matching vanilla's locator bar. |
| `hide-when.head-items` | pumpkin and mob heads | Head items that hide whoever wears them. |
| `precision.direction-steps` | `0` | Rounds directions so players cannot be triangulated. Player Locator Plus uses `300`. |
| `precision.send-distance` | `true` | When off, CrowBar shows only which way players are, with `?m` for the distance. |
| `precision.round-distance-beyond` / `round-distance-to` | `200` / `0` | Rounds far distances to a multiple. |
| `colors.mode` | `team` | `team`, `unique`, `constant`, or `custom` (players pick with `/crowbar color`). |
| `colors.players` | none | Fixed colors by player name, like Allium's `player-colors`. |

**Claim waypoints.** With GriefPrevention or GriefPrevention3D installed, claims appear on the locator bar as bowtie markers to their owner and anyone trusted on them, the same markers GPExpansion publishes. CrowBar players see every claim they have access to at any distance. On Paper 1.21.6 and newer, players without CrowBar get the markers on their vanilla locator bar too, within `claim-waypoints.vanilla-marker-range` (1000 blocks by default). Subdivisions and admin claims are off by default.

If Allium or GPExpansion is installed, the matching part steps aside so nothing is sent twice. GPExpansion's claim markers add custom names, colors, claim spawns and published claims; this plugin names claims after their owner and anchors them at their center.

| Permission | Default | |
| --- | --- | --- |
| `crowbar.locator` | everyone | Receive other players' positions. |
| `crowbar.locator.see-hidden` | op | See players who are sneaking, invisible, spectating or wearing a hiding head item. The equivalent of Allium's `allium.crowbar`. Vanished players stay hidden from anyone their vanish plugin hides them from. |
| `crowbar.locator.hidden` | nobody | Never appear on anyone's bar. |
| `crowbar.color` / `crowbar.color.others` | everyone / op | `/crowbar color <#RRGGBB\|reset> [player]` |
| `crowbar.claims` | everyone | `/crowbar claims <own\|trusted\|off>`: which claims show on your vanilla bar. |
| `crowbar.claims.admin` | op | See admin claims when `claim-waypoints.admin-claims` is on. |
| `crowbar.reload` | op | `/crowbar reload` |

The plugin runs on any server from Minecraft 1.21 to 26.3 regardless of which CrowBar jar carries it. Vanilla claim markers need Paper on 1.21.6 or newer; elsewhere only CrowBar players see claims.

## Allium Servers

Allium Essentials is an optional Paper server plugin that can send CrowBar a dedicated locator payload. When Allium is installed, CrowBar does not have to rely on vanilla locator waypoints: the server can send the player UUID, position, team color, and visibility state needed for the custom locator bar.

That lets CrowBar keep rendering players who are hidden, sneaking, invisible, or otherwise missing from vanilla waypoint data. It also lets the server keep NPC waypoint transmit range at zero while still giving CrowBar the real player data it needs.

Without Allium, CrowBar falls back to normal client-visible players, including LAN and integrated-server sessions.

<p>
  <a href="https://modrinth.com/plugin/allium-essentials#download"><img alt="Download Allium on Modrinth" src="https://img.shields.io/badge/Download%20Allium-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white"></a>
</p>

## GPX-CrowBar

When [GPExpansion](https://github.com/castledking/GPExpansion) is installed on the server, claim waypoints display the claim name and a bowtie marker on the locator bar to the claim owner and trusted players. CrowBar reads the same locator data and renders the name tag above the waypoint, giving you readable claim labels right on the locator bar.

![GPX-CrowBar](https://castled.codes/assets/gpx-crowbar.png)

## Requirements

Use the jar that matches your Minecraft line and loader:

### Fabric

- `CrowBar-1.21-1.21.1`: Minecraft 1.21-1.21.1, Java 21, Fabric Loader 0.18.4 or newer, Fabric API 0.100.1 or newer.
- `CrowBar-1.21.2-1.21.5`: Minecraft 1.21.2-1.21.5, Java 21, Fabric Loader 0.18.4 or newer, Fabric API 0.102.2 or newer.
- `CrowBar-1.21.6-1.21.8`: Minecraft 1.21.6-1.21.8, Java 21, Fabric Loader 0.18.4 or newer, Fabric API 0.125.0 or newer.
- `CrowBar-1.21.x`: Minecraft 1.21.9-1.21.11, Java 21, Fabric Loader 0.18.4 or newer, Fabric API 0.134.0 or newer.
- `CrowBar-26.1.x`: Minecraft 26.1.x, Java 25, Fabric Loader 0.19.2 or newer, Fabric API 0.145.1 or newer.
- `CrowBar-26.x`: Minecraft 26.2, Java 25, Fabric Loader 0.19.3 or newer, Fabric API 0.152.2 or newer.
- `CrowBar-26.3.x`: Minecraft 26.3, Java 25, Fabric Loader 0.19.5 or newer, Fabric API 0.160.7 or newer.

### NeoForge

- `CrowBar-1.21-1.21.1-neoforge`: Minecraft 1.21-1.21.1, Java 21, NeoForge 21.0 or newer.
- `CrowBar-1.21.3-1.21.5-neoforge`: Minecraft 1.21.3-1.21.5, Java 21, NeoForge 21.3 or newer. NeoForge never had a stable 1.21.2 release.
- `CrowBar-1.21.6-1.21.8-neoforge`: Minecraft 1.21.6-1.21.8, Java 21, NeoForge 21.6 or newer.
- `CrowBar-1.21.9-1.21.10-neoforge`: Minecraft 1.21.9-1.21.10, Java 21, NeoForge 21.9 or newer.
- `CrowBar-1.21.x-neoforge`: Minecraft 1.21.11, Java 21, NeoForge 21.11 or newer.
- `CrowBar-26.1.x-neoforge`: Minecraft 26.1.x, Java 25, NeoForge 26.1 or newer.
- `CrowBar-26.2.x-neoforge`: Minecraft 26.2, Java 25, NeoForge 26.2 or newer.
- `CrowBar-26.3.x-neoforge`: Minecraft 26.3, Java 25, NeoForge 26.3 (beta) or newer.

Mod Menu is optional on Fabric. The settings screen is always available on the `\` key, and on NeoForge through the mod list's Config button.

## Controls

Default keybinds:

- `N`: Toggle locator name tags
- `B`: Toggle locator skins
- `Z`: Toggle claim waypoints
- `X`: Show distance
- `\`: Open CrowBar settings
- View self: unbound

Keybinds can be changed from Minecraft's controls menu under the CrowBar category, or from the Key Binds button on the settings screen's Controls tab.

Each feature key (name tags, skins, distance, view self) can be set to **Toggle** or **Hold** on the Controls tab. Toggle flips the feature with each press and remembers the choice. Hold shows the feature only while the key is down, for example holding `X` to check distances.

## Settings

The settings screen has three tabs. The defaults keep CrowBar's original look, so every appearance setting is opt-in.

**General:** name tags, skins, distance, view self, and height arrows.

**Appearance:**

| Setting | Default | What it does |
| --- | --- | --- |
| Label background | Off | Draws a dark plate behind each label so it stays readable over bright terrain. |
| Raise HUD | Off | Smoothly lifts hearts, hunger, the experience level and chat above the labels while any label is showing. |
| Dim other labels | On | Draws every label except the one nearest your crosshair at reduced opacity. |
| Hide overlapping labels | Off | Skips labels that would overlap one nearer your crosshair. |
| Skin frame | Off | Draws faces inside a frame in the player's dot color, so team colors stay visible with skins on. |
| Fade distant markers | Off | Fades markers out between 100 and 1000 blocks. |
| Dot colors | Team | Team color or a color unique to each player, a color per player ignoring teams, or one single color for everyone. |
| Single color | White | The color used by the Single color mode. |
| Your dot color | Automatic | Your own marker's color in view-self mode. Other players' screens are not affected. |

**Controls:** Toggle or Hold for each feature key, and a shortcut to Minecraft's key binds.

Settings are saved to `config/crowbar.json`. A few values only live in the file: `fadeStart`, `fadeEnd` and `fadeEndOpacity` set the fade range, and `customDotColor` and `selfDotColor` accept any `#RRGGBB` color, not just the colors the screen cycles through.

On servers without Allium, Minecraft 1.21.6 and later draw the locator bar dots themselves. CrowBar still applies dot colors there by repainting vanilla's dots, but those dots cannot fade, so only faces and labels fade, and vanilla's own height arrows always show.

## Installation

### Fabric

1. Install Fabric Loader for your Minecraft version.
2. Install Fabric API.
3. Put the matching CrowBar jar into your `mods` folder.
4. Launch the game.

### NeoForge

1. Install NeoForge for your Minecraft version.
2. Put the matching CrowBar NeoForge jar into your `mods` folder.
3. Launch the game.

## Building

```bash
./gradlew build
# or per version/loader:
./gradlew :version21_0:fabric:build :version21_2:neoforge:build :version21_6:fabric:build
```

Each supported Minecraft line has `fabric` and `neoforge` subprojects:

| Directory     | Minecraft            | Loaders           |
| ------------- | -------------------- | ----------------- |
| `version21_0` | 1.21 – 1.21.1       | Fabric, NeoForge  |
| `version21_2` | 1.21.2 – 1.21.5 (NeoForge 1.21.3 – 1.21.5) | Fabric, NeoForge  |
| `version21_6` | 1.21.6 – 1.21.8     | Fabric, NeoForge  |
| `version21_9` | 1.21.9 – 1.21.11    | Fabric, NeoForge  |
| `version21`   | 1.21.11              | NeoForge          |
| `version26_1` | 26.1.x               | Fabric, NeoForge  |
| `version26_2` | 26.2.x               | Fabric, NeoForge  |
| `version26_3` | 26.3.x               | Fabric, NeoForge  |

`bukkit/` holds the server plugin. Every loader jar bundles it (see `gradle/universal-jar.gradle`), and `bukkit/build/libs/CrowBar-plugin-<version>.jar` is the plugin on its own.

The built jars are written to `versionXX/<loader>/build/libs/`. Loader-agnostic logic shared by all jars lives in `common/` and is bundled into each jar: the settings model and its file, hold/toggle key handling, the HUD lift animation, label overlap rules, dot colors and fading. Every jar ships its own copy of `assets/crowbar/lang/en_us.json`; the common tests fail if the copies drift apart or miss a key the settings screen uses.

NeoForge notes: the NeoForge jars use ModDevGradle and compile against official mappings; the 26.x NeoForge subprojects target Java 25 (their Minecraft line requires it), everything else targets Java 21. NeoForge builds are split per Minecraft line because Mojang's class renames (e.g. `ResourceLocation` → `Identifier` in 1.21.11) change the API surface. 26.3 also replaced GLFW with SDL3, so its keybinds and modifier checks go through `InputConstants` instead of LWJGL's GLFW bindings.

## Links

- Website: https://castled.codes
- Issues: https://github.com/castledking/CrowBar/issues
- Upstream inspiration: https://github.com/MCRcortex/headlocatorbar
- Experience bar markers, label plates, HUD lift and distance fading were inspired by Player Locator Plus: https://github.com/timas130/PlayerLocatorPlus
