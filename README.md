[![CrowBar](https://castled.codes/assets/crowbar-banner.png)](https://github.com/castledking/CrowBar)

<p align="center">
  <a href="https://discord.com/invite/pCKdCX6nYr"><img alt="Discord" src="https://img.shields.io/badge/Discord-Community-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/castledking/CrowBar/issues"><img alt="GitHub Issues" src="https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/castledking/CrowBar/wiki"><img alt="Wiki" src="https://img.shields.io/badge/GitHub-Wiki-181717?style=for-the-badge&logo=github"></a>
  <a href="https://castled.codes"><img alt="CASTLED CODEX" src="https://castled.codes/assets/logo-banner.png" width="140" height="35"></a>
</p>

CrowBar is a Fabric client mod for Minecraft 1.21.6-1.21.11, 26.1.x, and 26.2 that extends the locator bar with readable player name tags, skin markers, distance text, team-colored dots, and a self-view capture mode.

## Features

- Renders player names above locator bar markers.
- Can replace locator dots with player skin faces.
- Shows optional distance text next to player names.
- Preserves team colors when a team color is available.
- Adds a self-view mode for clean locator bar screenshots.
- Hides hearts, hunger, armor, and the vanilla locator bar while self-view mode is active.
- Works as a client-side mod; servers do not need to install CrowBar.
- Includes Mod Menu support for toggling features in-game.

## Allium Servers

Allium Essentials is an optional Paper server plugin that can send CrowBar a dedicated locator payload. When Allium is installed, CrowBar does not have to rely on vanilla locator waypoints: the server can send the player UUID, position, team color, and visibility state needed for the custom locator bar.

That lets CrowBar keep rendering players who are hidden, sneaking, invisible, or otherwise missing from vanilla waypoint data. It also lets the server keep NPC waypoint transmit range at zero while still giving CrowBar the real player data it needs.

Without Allium, CrowBar falls back to normal client-visible players, including LAN and integrated-server sessions.

<p>
  <a href="https://modrinth.com/plugin/allium-essentials#download"><img alt="Download Allium on Modrinth" src="https://img.shields.io/badge/Download%20Allium-Modrinth-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white"></a>
</p>

## Requirements

Use the jar that matches your Minecraft line:

- `CrowBar-1.21.x`: Minecraft 1.21.6-1.21.11, Java 21, Fabric Loader 0.18.4 or newer, Fabric API 0.141.4 or newer.
- `CrowBar-26.1.x`: Minecraft 26.1.x, Java 25, Fabric Loader 0.19.2 or newer, Fabric API 0.150.0 or newer.
- `CrowBar-26.x`: Minecraft 26.2, Java 25, Fabric Loader 0.19.3 or newer, Fabric API 0.152.2 or newer.

Mod Menu is optional, but recommended for the config screen.

## Controls

Default keybinds:

- `N`: Toggle locator name tags
- `B`: Toggle locator skins
- `Z`: View self locator bar
- `X`: Show distance

Keybinds can be changed from Minecraft's controls menu under the CrowBar category.

## Installation

1. Install Fabric Loader for your Minecraft version.
2. Install Fabric API.
3. Put the matching CrowBar jar into your `mods` folder.
4. Launch the game.

## Building

```bash
./gradlew :version21:build :version26_1:build :version26_2:build
```

The built jars are written to `version21/build/libs/`, `version26_1/build/libs/`, and `version26_2/build/libs/`.

## Links

- Website: https://castled.codes
- Issues: https://github.com/castledking/CrowBar/issues
- Upstream inspiration: https://github.com/MCRcortex/headlocatorbar
