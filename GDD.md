# GDD: Reelcraft

**Pitch:** Minecraft where *Singleplayer* opens an endless, Instagram-Reels-style vertical feed of worlds. Each "reel" is a fresh place to play in. When you're done, you scroll to the next one. The next 5 are always pre-generated, so scrolling is instant.

**Target:** Minecraft 26.3, Fabric (Loader 0.20.1, Fabric API 0.165.0+26.3), Java 25. Mod id `reelcraft`, package `com.reelcraft`.

## Design decisions
- **One integrated server, many "worlds".** Vanilla can only run one server at a time, so every reel is a far-apart location (a distinct biome/terrain) in a single hidden world. Reels are spaced millions of blocks apart. "New world" = new random location, found and chunk-generated ahead of time.
- **Singleplayer button** on the title screen skips the world-select screen. It wipes the previous feed world, creates a fresh random-seed survival world, and drops the player into reel #1.
- **Feed buffer.** The server keeps `BUFFER_AHEAD = 5` reels generating/ready ahead of the current one. Each reel holds chunk tickets (`TicketType.FORCED`) until it falls behind the history window. Scrolling teleports the player to the next reel's spawn, which is already loaded.
- **Each reel is a fresh run.** On arrival: inventory reset, full health/food, small starter kit, respawn point set to the reel spawn. Dying respawns you at the reel start.
- **Scrolling.** Mouse wheel (hotbar still on 1-9), Up/Down arrows as rebindable keys. A vertical swipe transition covers the teleport.

## UI (mirrors Instagram Reels, Minecraftified)
Portrait 9:16 column centred in the window, pillarboxed with dark bars; the world is the "video".
- **Top bar:** "Reels" title (left), camera icon (right), buffer pips showing which upcoming reels are ready.
- **Right rail:** heart (likes, real Minecraft heart sprite), comment bubble (count), paper-plane share, three dots, spinning music-disc audio thumbnail.
- **Bottom-left:** avatar + `@biome_name` creator handle + Follow button, caption (biome / coordinates / seed tag), "Original audio" ticker with note icon.
- **Bottom nav:** home / search / reels (selected) / shop / profile icons.
- Pixel-art white sprites tinted at runtime, rendered with the vanilla Minecraft font and dark inventory-style panels so it feels diegetic.

## Milestones
1. [x] Scaffold from fabric-example-mod (26.3), rename to Reelcraft.
2. [ ] Title screen Singleplayer -> auto-created feed world.
3. [ ] Server-side `ReelFeed`: reel model, location search, chunk pre-generation buffer, teleport/reset on scroll.
4. [ ] Input: scroll wheel + keybinds.
5. [ ] Reels HUD (pillarbox, rails, nav, buffer pips).
6. [ ] Swipe transition.
7. [ ] Polish: likes/comments flavour, death handling, config, lang file.
