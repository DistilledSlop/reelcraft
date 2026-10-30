package com.reelcraft.feed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * One "video" in the feed: a far-away spot in the hidden feed world that is generated ahead of time.
 * Mutable fields are written on the server thread and read by the client HUD, hence volatile.
 */
public final class Reel {
	public enum State { SEARCHING, GENERATING, READY }

	private static final String[] CAPTIONS = {
		"no one asked but here is my base spot",
		"POV: you spawn here with 0 plans",
		"day 1 of surviving on vibes only",
		"wait for the creeper at the end",
		"this seed is actually insane",
		"bro really built here",
		"rate this spawn 1-10",
		"touch grass (it's right here)",
	};

	public final long id;
	public volatile State state = State.SEARCHING;
	public volatile ChunkPos chunk;
	public volatile BlockPos spawn;
	public volatile String biome = "unknown";
	public volatile int attempts;

	public Reel(long id, ChunkPos chunk) {
		this.id = id;
		this.chunk = chunk;
	}

	public boolean isReady() {
		return this.state == State.READY;
	}

	private long hash(long salt) {
		long h = (this.id + 1) * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return h & Long.MAX_VALUE;
	}

	/** Creator handle, Minecraftified: the biome is the "account". */
	public String handle() {
		return "@" + this.biome + "_" + (this.hash(1) % 90 + 10);
	}

	public String caption() {
		return CAPTIONS[(int) (this.hash(2) % CAPTIONS.length)];
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
