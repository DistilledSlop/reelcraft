package com.reelcraft.feed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * One "video" in the feed: a far-away spot in the hidden feed world that is generated ahead of time.
 * Mutable fields are written on the server thread and read by the client HUD, hence volatile.
 */
public final class Reel {
	public enum State { SEARCHING, GENERATING, READY }

	private static final String[] HANDLE_A = {
		"creeper", "zombie", "enderman", "steve", "alex", "villager", "piglin", "axolotl", "redstone", "obsidian",
		"diamond", "netherite", "cobble", "pixel", "blocky", "sneaky", "crafty", "lava", "ghast", "golem", "wither",
		"skeleton", "parrot", "llama", "cherry", "mossy", "deepslate", "copper", "amethyst", "glow", "tnt", "enchanted",
		"bedrock", "sculk", "warden", "allay", "frog", "bee", "wolf", "ocelot",
	};
	private static final String[] HANDLE_B = {
		"enjoyer", "gamer", "builds", "speedruns", "vibes", "daily", "clips", "core", "lore", "mains", "tutorials",
		"diaries", "irl", "official", "hq", "fan", "tv", "plays", "moments", "pov", "heaven", "goblin", "kid", "rat",
		"king", "queen", "dad", "mom", "andy", "supply",
	};
	private static final String[] TRACKS = {
		"Sweden - C418", "Wet Hands - C418", "Subwoofer Lullaby - C418", "Mice on Venus - C418", "Aria Math - C418",
		"Dry Hands - C418", "Haggstrom - C418", "Danny - C418", "Living Mice - C418", "Moog City - C418",
		"Blind Spots - C418", "Pigstep - Lena Raine", "Otherside - Lena Raine", "Creator - Lena Raine",
		"Ancestry - Lena Raine", "Infinite Amethyst - Lena Raine", "Floating Trees - Aaron Cherof",
		"Eld Unknown - Samuel Aberg", "Original audio",
	};
	private static final String[] GENERIC_CAPTIONS = {
		"no one asked but here is my base spot", "POV: you spawn here with 0 plans", "day 1 of surviving on vibes only",
		"wait for the creeper at the end", "this seed is actually insane", "bro really built here",
		"rate this spawn 1-10", "touch grass (it's right here)", "tell me you're lost without telling me",
		"it's giving chunk border", "my villagers are unionizing", "unexpected diamonds??", "no shaders, no mods, just vibes",
		"found this at 3am and can't stop thinking about it", "the audacity of this terrain", "NPC behavior fr",
		"say less, I'm moving in", "who needs a bed anyway", "the render distance did NOT prepare me",
		"this is why I don't sleep", "POV: night falls in 4 minutes", "built different (literally, one block at a time)",
		"I swear this wasn't here yesterday", "spawn luck is not a real thing (it is)", "save this for later",
		"you won't believe what's behind that hill", "part 2 if this hits 1k", "mom come pick me up I'm scared",
	};
	private static final String[][] BIOME_CAPTIONS = {
		{"desert|badlands|mesa", "no water for 400 blocks, send help", "the sand gets everywhere", "dune season is here", "cactus to the face speedrun"},
		{"snow|ice|frozen|grove|peaks", "it's giving frostbite", "powder snow is a trap", "cold open, colder spawn", "where do I even put a campfire"},
		{"jungle|bamboo", "ocelots, parrots and a very long walk", "lost in the canopy again", "vines vines vines"},
		{"swamp|mangrove", "the frogs are watching", "mud, mud and more mud", "witch hut energy"},
		{"forest|taiga|birch|cherry|dark_forest", "wood is free here, you're welcome", "one tree away from a house", "cherry grove hits different", "do not go in the dark forest"},
		{"ocean|beach|river|shore", "a whole ocean and no boat", "drowned on sight", "beach episode but make it survival"},
		{"mountain|hills|slopes|stony|windswept|jagged", "the view is free, the climb is not", "goat season", "fall damage speedrun any%"},
		{"mushroom", "mooshrooms!!! no mobs spawn, perfect base", "this biome is so rare it's actually crazy"},
		{"savanna", "acacia core", "flat top trees for the aesthetic", "llamas in the distance"},
		{"plains|meadow", "flat land, infinite possibilities", "perfect build spot, change my mind", "villager trade hall loading"},
		{"dripstone|lush|cave|deep_dark", "I think I'm underground?", "axolotls hiding somewhere", "do not dig down"},
	};

	public final long id;
	private final long salt;
	public volatile State state = State.SEARCHING;
	public volatile ChunkPos chunk;
	public volatile BlockPos spawn;
	public volatile String biome = "unknown";
	public volatile int attempts;

	/** {@code salt} is random per reel, so handles, captions and counts differ every session. */
	public Reel(long id, long salt, ChunkPos chunk) {
		this.id = id;
		this.salt = salt;
		this.chunk = chunk;
	}

	public boolean isReady() {
		return this.state == State.READY;
	}

	private long hash(long stream) {
		long h = this.salt * 0x9E3779B97F4A7C15L ^ (stream + 1) * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return h & Long.MAX_VALUE;
	}

	private <T> T pick(T[] values, long stream) {
		return values[(int) (this.hash(stream) % values.length)];
	}

	/** Creator handle, e.g. {@code @sneaky.llama}; unique-ish per reel. */
	public String handle() {
		String a = this.pick(HANDLE_A, 1);
		String b = this.pick(HANDLE_B, 2);
		long n = this.hash(3);
		String name = switch ((int) (n % 5)) {
			case 0 -> a + "_" + b;
			case 1 -> a + "." + b;
			case 2 -> a + b + (n / 5 % 90 + 10);
			case 3 -> "the" + a + "_" + b;
			default -> a + "_" + b + (n / 5 % 9 + 1);
		};
		return "@" + name;
	}

	/** Sound line under the caption: a real Minecraft track, or "Original audio" like most reels. */
	public String audio() {
		String track = this.pick(TRACKS, 6);
		return track.equals("Original audio") ? "Original audio \u00b7 " + this.biome.replace('_', ' ') : track;
	}

	public String caption() {
		String[] biomeLines = null;
		for (String[] group : BIOME_CAPTIONS) {
			for (String key : group[0].split("\\|")) {
				if (this.biome.contains(key)) {
					biomeLines = group;
					break;
				}
			}
			if (biomeLines != null) {
				break;
			}
		}
		if (biomeLines != null && this.hash(4) % 100 < 60) {
			return biomeLines[1 + (int) (this.hash(5) % (biomeLines.length - 1))];
		}
		return this.pick(GENERIC_CAPTIONS, 5);
	}

	public int likes() {
		return (int) (this.hash(3) % 98_000) + 120;
	}

	public int comments() {
		return (int) (this.hash(4) % 3_400) + 3;
	}

	public int shares() {
		return (int) (this.hash(5) % 900) + 1;
	}

	public static String compact(int n) {
		if (n >= 10_000) {
			return (n / 1000) + "K";
		}
		if (n >= 1000) {
			return (n / 100) / 10.0 + "K";
		}
		return Integer.toString(n);
	}
}
