# GDD: Reelcraft

**Pitch:** Minecraft where *Singleplayer* opens an endless, Instagram-Reels-style vertical feed of worlds. Each "reel" is a fresh place to play in. When you're done, you scroll to the next one. The next 5 are always pre-generated, so scrolling is instant.

**Target:** Minecraft 26.3, Fabric (Loader 0.20.1, Fabric API 0.165.0+26.3), Java 25. Mod id `reelcraft`, package `com.reelcraft`.

## Design decisions
- **Every reel is a real, separate world.** The integrated server creates one extra overworld-type level per reel at runtime (`ReelLevels`), each with its own random seed and its own chunk generator. A small mixin makes `ServerLevel.getSeed()` return the level's own seed (vanilla returns one server-wide seed). Reel levels never save to disk. Levels more than `HISTORY` reels behind the player are closed.
- **Singleplayer button** on the title screen skips the world-select screen. It wipes the previous feed world, creates a fresh random-seed survival world, and drops the player into reel #1.
- **Feed buffer.** The server keeps `BUFFER_AHEAD = 5` reels created and chunk-generated ahead of the current one, and tops the queue up as you scroll. At startup the fill begins 6 seconds after the first reel starts, two worlds at a time, so the first reel's terrain loads first. Spawn spots are sampled from each new world's generator (dry land, preferring a biome none of the last 4 reels used). Scrolling teleports the player into the next reel's level, which is already loaded. Vanilla's "Loading terrain" screen is hidden during a scroll (the feed UI stays up), and the client is told every reel is the overworld so it keeps its level.
- **Each reel is a fresh run.** On arrival: inventory reset, full health/food, small starter kit, respawn point set to the reel spawn. Dying respawns you at the reel start.
- **Scrolling.** Mouse wheel (hotbar still on 1-9), Up/Down arrows as rebindable keys. The client grabs a frame of the current reel and freezes it over the screen while the next world loads, then slides it away (up when scrolling down, down when scrolling up, about 320 ms, ease-out) to reveal the already-generated next reel.

## UI (mirrors Instagram Reels, Minecraftified)
Portrait 9:16 column centred in the window, pillarboxed with dark bars; the world is the "video".
- **Top bar:** "Reels" title (left), camera icon (right), buffer pips showing which upcoming reels are ready.
- **Right rail:** heart (likes, real Minecraft heart sprite), comment bubble (count), paper-plane share, three dots, spinning music-disc audio thumbnail.
- **Bottom-left:** avatar + `@biome_name` creator handle + Follow button, caption (biome / coordinates / seed tag), "Original audio" ticker with note icon.
- **Bottom nav:** home / search / reels (selected) / shop / profile icons.
- Pixel-art white sprites tinted at runtime, rendered with the vanilla Minecraft font and dark inventory-style panels so it feels diegetic.

## Milestones
1. [x] Scaffold from fabric-example-mod (26.3), rename to Reelcraft.
2. [x] Title screen Singleplayer -> auto-created feed world.
3. [x] Server-side `ReelFeed`: reel model, location search, chunk pre-generation buffer, teleport/reset on scroll.
4. [x] Input: scroll wheel + keybinds.
5. [x] Reels HUD (pillarbox, rails, nav, buffer pips).
6. [x] Scroll animation: the old reel's frame is grabbed and slides away to reveal the preloaded next reel (no loading screen or curtain).
7. [ ] Polish: likes/comments flavour, death handling, config, lang file.
