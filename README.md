# Sailing Bad

A RuneLite Plugin Hub plugin that hides Sailing from the Old School RuneScape Skills tab and puts the total level back to what it was before Sailing existed. A maxed account reads 2277 again.

## Features

- Hides the Sailing tile from the Skills tab.
- Moves the Total level into the slot Sailing leaves behind, closing the grid back up to eight rows. Sailing turned it into a full-width bar on a row of its own, so it is resized back to a single tile and its label is stacked above the number to fit.
- Subtracts Sailing from the total level shown on the Total level tile.
- Replaces the Sailing hover text with a readable Total level and Total XP tooltip, with Sailing removed from both values.
- Uses one fixed, tested Total tile layout with no panel, position, or font adjustment controls.
- Adds one **Add me to the HiScores** button. When the separate HiScores opt-in setting is enabled, clicking it submits the current character name to `https://2277.telfardo.com/api/hiscores` and opens the saved result.
- Restores the native Skills interface immediately when the plugin or its layout options are turned off.
- Each part can be turned off on its own in the plugin settings.

The Skills-tab changes are display-only. They change nothing sent to or stored by the game, so your real Sailing level and real total level remain untouched.

The community HiScores feature is separate, disabled by default, and requires two deliberate actions: enable **HiScores opt-in** in the plugin settings, then click **Add me to the HiScores** in the side panel. That sends the current character name and IP address to `2277.telfardo.com`, where the player is stored in the opted-in leaderboard. Ordinary website searches use GET and do not add a player.

## Development

Requires Java 11.

```text
./gradlew test
./gradlew run
./gradlew runManualLogin
```

`runManualLogin` starts the development client with an isolated RuneLite home and no cached Jagex Launcher session, allowing the normal username-and-password login screen without touching your regular RuneLite credentials or settings.

When the development client is open, sign in and open the Skills tab. Confirm Sailing is gone, that Total level has moved up into the bottom-right slot, and that the total no longer counts Sailing. Hover the tile and confirm the tooltip says **Total XP** and excludes Sailing XP. Gain a level to make the game rebuild the tab and confirm the overrides survive it. Untick each setting in turn and confirm the tab goes back to normal.

## Licence

BSD 2-Clause. See `LICENSE`.
