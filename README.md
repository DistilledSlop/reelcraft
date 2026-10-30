# Reelcraft

Reelcraft is Minecraft reimagined for singleplayer as an endless, vertical, Instagram Reels-style feed of worlds. Each reel is a fresh, ready-to-play map: play it, scroll to the next one, and it is already waiting for you.

## How it works

- Clicking **Singleplayer** on the title screen skips the world list and opens the feed.
- Each reel is its own separate world with its own random seed, created at runtime and never saved to disk.
- The next 5 reels are pre-generated ahead of you, so scrolling is instant.
- Scrolling teleports you into an already-loaded world. Each arrival is a fresh run: inventory reset, full health and food, a small starter kit, and your respawn point set to the reel's spawn.
- The scroll animation grabs a frame of the current reel, holds it over the screen while the next world loads, then slides it away (about 320 ms) to reveal the next reel. No loading screen is shown during a scroll.
- At startup the buffer fills gradually: it starts about 6 seconds after the first reel begins, and at most 2 worlds generate at once, so the first reel loads first.

## Controls

| Input | Action |
| --- | --- |
| Mouse wheel, Up / Down arrows | Scroll to the previous / next reel |
| `J` | Like the current reel |
| `1`-`9` | Select hotbar slot |
| Shift-click **Singleplayer** | Open the classic world list |

The scroll keys are rebindable under the Reelcraft category in Controls.

## Building and running

```sh
./gradlew build              # compile and package the mod into build/libs/
./gradlew runClient          # launch the game with the mod loaded
./gradlew runClientGameTest  # drive the feed in a real client and write screenshots
```

`runClientGameTest` opens a real game window. Screenshots are written to `build/run/clientGameTest/screenshots`.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1
- Fabric API 0.165.0+26.3
- Java 25

## Project layout

- `src/main/java/com/reelcraft/feed/`: server-side reel model, per-reel levels, and the pre-generation buffer.
- `src/client/java/com/reelcraft/client/`: input, the Reels HUD, and client mixins.
- `GDD.md`: the full game design document.

## License

Reelcraft is free software, licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0-only).
