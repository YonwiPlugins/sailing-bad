# Sailing Bad

A RuneLite Plugin Hub plugin that hides Sailing from the Old School RuneScape Skills tab and puts the total level back to what it was before Sailing existed. A maxed account reads 2277 again.

## Features

- Hides the Sailing tile from the Skills tab.
- Moves the Total level into the slot Sailing leaves behind, closing the grid back up to eight rows. Sailing turned it into a full-width bar on a row of its own, so it is resized back to a single tile and its label is stacked above the number to fit.
- Subtracts Sailing from the total level shown on the Total level tile.
- Subtracts Sailing from the total level and total XP shown in the Total level tooltip.
- Each part can be turned off on its own in the plugin settings.

This is a display-only plugin. It rewrites what the Skills tab draws and changes nothing that is sent to or stored by the game, so your real Sailing level, your real total level, and the hiscores are all untouched.

## Development

Requires Java 11.

```text
./gradlew test
./gradlew run
```

When the development client is open, sign in and open the Skills tab. Confirm Sailing is gone, that Total level has moved up into the bottom-right slot, and that the total no longer counts Sailing. Hover Total level and confirm the tooltip agrees. Gain a level to make the game rebuild the tab and confirm the overrides survive it. Untick each setting in turn and confirm the tab goes back to normal.

## Licence

BSD 2-Clause. See `LICENSE`.
