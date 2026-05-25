# CrowBar

CrowBar is a Fabric client mod for Minecraft 1.21.11 that extends the vanilla locator bar with readable player name tags, skin markers, and a self-view capture mode.

## Features

- Renders player names above locator bar markers.
- Can replace locator dots with player skin faces.
- Adds a self-view mode for clean locator bar screenshots.
- Hides hearts, hunger, armor, and the vanilla locator bar while self-view mode is active.
- Works as a client-side mod; servers do not need to install CrowBar.
- Includes Mod Menu support for toggling features in-game.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Fabric API 0.141.4 or newer
- Java 21
- Mod Menu is optional, but recommended for the config screen.

## Controls

Default keybinds:

- `N`: Toggle locator name tags
- `B`: Toggle locator skins
- `V`: Toggle self-view locator bar

Keybinds can be changed from Minecraft's controls menu under the CrowBar category.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Fabric API.
3. Put the CrowBar jar into your `mods` folder.
4. Launch the game.

## Building

```bash
./gradlew build
```

The built jar is written to `build/libs/`.

## Links

- Website: https://castled.codes
- Issues: https://github.com/castledking/CrowBar/issues
- Upstream inspiration: https://github.com/MCRcortex/headlocatorbar
