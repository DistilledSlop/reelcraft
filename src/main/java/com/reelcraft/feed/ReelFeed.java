package com.reelcraft.feed;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.reelcraft.Reelcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelData;

/**
 * Server-side feed. Keeps the current reel plus {@link #BUFFER_AHEAD} reels searched, generated and chunk-loaded
 * ahead of time, so scrolling is just a teleport onto already-loaded terrain.
 */
public final class ReelFeed {
	public static final int BUFFER_AHEAD = 5;
	/** Reels behind the current one that keep their chunks, so scrolling back is instant too. */
	public static final int HISTORY = 1;
	/** Chunk radius generated per reel before it becomes READY. */
	public static final int PRELOAD_RADIUS = 4;
	private static final int SPREAD_CHUNKS = 150_000;
	private static final int MAX_ATTEMPTS = 24;
	/** A new reel avoids the biomes of this many reels before it, when it can. */
	private static final int VARIETY_WINDOW = 4;

	private final MinecraftServer server;
	private final RandomSource random = RandomSource.create();
	private final List<Reel> reels = new CopyOnWriteArrayList<>();
	private volatile int current = -1;
	private int pendingScroll;
	private long nextId;
	private volatile boolean stopping;

	public ReelFeed(MinecraftServer server) {
		this.server = server;
	}

	public List<Reel> reels() {
		return this.reels;
	}

	/** Index of the reel the player is on, or -1 before the feed has started. */
	public int current() {
		return this.current;
	}

	public Reel currentReel() {
		int i = this.current;
		return i >= 0 && i < this.reels.size() ? this.reels.get(i) : null;
	}

	/** Called when the player joins: the world spawn they landed on becomes reel #1. */
	public void begin(ServerPlayer player) {
		if (!this.reels.isEmpty()) {
			return;
		}
		Reel first = new Reel(this.nextId++, this.random.nextLong(), player.chunkPosition());
		first.spawn = player.blockPosition();
		first.biome = biomeName(player.level(), first.spawn);
		first.state = Reel.State.READY;
		this.reels.add(first);
		this.current = 0;
		this.enter(player, first);
	}

	/** Ask to move {@code dir} reels (+1 next, -1 previous). Safe to call from any thread. */
	public void requestScroll(int dir) {
		this.server.execute(() -> this.pendingScroll = Integer.signum(dir));
	}

	/** Stop all background work and release every chunk ticket so the server can shut down quickly. */
	public void shutdown() {
		this.stopping = true;
		ServerLevel level = this.server.overworld();
		for (Reel reel : this.reels) {
			if (reel.chunk != null && reel.state != Reel.State.SEARCHING) {
				level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, reel.chunk, PRELOAD_RADIUS);
				reel.chunk = null;
			}
		}
	}

	public void tick() {
		if (this.stopping || this.current < 0) {
			return;
		}
		int ahead = this.reels.size() - 1 - this.current;
		if (ahead < BUFFER_AHEAD) {
			Reel reel = new Reel(this.nextId++, this.random.nextLong(), randomChunk());
			this.reels.add(reel);
			this.search(reel);
		}
		if (this.pendingScroll != 0) {
			int target = this.current + this.pendingScroll;
			if (target >= 0 && target < this.reels.size() && this.reels.get(target).isReady()) {
				this.pendingScroll = 0;
				this.moveTo(target);
			} else if (target < 0) {
				this.pendingScroll = 0;
			}
			// Otherwise leave the request pending until the next reel finishes generating.
		}
	}

	private void moveTo(int index) {
		ServerPlayer player = this.server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
		if (player == null) {
			return;
		}
		this.current = index;
		this.enter(player, this.reels.get(index));
		this.releaseOld();
	}

	private void releaseOld() {
		ServerLevel level = this.server.overworld();
		for (int i = 0; i < this.current - HISTORY; i++) {
			Reel old = this.reels.get(i);
			if (old.chunk != null && old.state != Reel.State.SEARCHING) {
				level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, old.chunk, PRELOAD_RADIUS);
				old.chunk = null;
			}
		}
	}

	private ChunkPos randomChunk() {
		return new ChunkPos(this.random.nextInt(-SPREAD_CHUNKS, SPREAD_CHUNKS), this.random.nextInt(-SPREAD_CHUNKS, SPREAD_CHUNKS));
	}

	/** Step 1: load only the centre chunk and make sure the spot is land worth looking at. */
	private void search(Reel reel) {
		if (this.stopping) {
			return;
		}
		ServerLevel level = this.server.overworld();
		ChunkPos pos = reel.chunk;
		reel.attempts++;
		level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, pos, 0).thenRunAsync(() -> {
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getMiddleBlockPosition(0));
			String biome = biomeName(level, surface);
			boolean bad = !level.getFluidState(surface.below()).isEmpty()
				|| surface.getY() <= level.getSeaLevel() - 2
				|| this.recentlySeen(reel, biome);
			level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pos, 0);
			if (this.stopping) {
				return;
			}
			if (bad && reel.attempts < MAX_ATTEMPTS) {
				reel.chunk = randomChunk();
				this.search(reel);
				return;
			}
			reel.spawn = surface;
			reel.biome = biome;
			reel.state = Reel.State.GENERATING;
			this.generate(reel);
		}, this.server);
	}

	/** True if one of the few reels before this one already used the same biome. */
	private boolean recentlySeen(Reel reel, String biome) {
		int index = this.reels.indexOf(reel);
		for (int i = Math.max(0, index - VARIETY_WINDOW); i < index; i++) {
			if (this.reels.get(i).biome.equals(biome)) {
				return true;
			}
		}
		return false;
	}

	/** Step 2: generate the surroundings so the player never sees a loading screen. */
	private void generate(Reel reel) {
		if (this.stopping) {
			reel.chunk = null;
			return;
		}
		ServerLevel level = this.server.overworld();
		ChunkPos pos = reel.chunk;
		level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, pos, PRELOAD_RADIUS).thenRunAsync(() -> {
			if (this.stopping) {
				return;
			}
			reel.state = Reel.State.READY;
			Reelcraft.LOGGER.debug("Reel {} ready at {} ({})", reel.id, reel.spawn, reel.biome);
		}, this.server);
	}

	/** Drop the player into a reel as a fresh run. */
	private void enter(ServerPlayer player, Reel reel) {
		ServerLevel level = this.server.overworld();
		BlockPos spawn = reel.spawn;
		float yaw = this.random.nextFloat() * 360.0F - 180.0F;
		player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.<Relative>of(), yaw, 10.0F, true);
		player.setDeltaMovement(0, 0, 0);
		player.resetFallDistance();
		player.getInventory().clearContent();
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.removeAllEffects();
		player.setRespawnPosition(
			new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(level.dimension(), spawn), yaw, 10.0F), true), false
		);
		player.getInventory().add(new ItemStack(Items.STONE_SWORD));
		player.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
		player.getInventory().add(new ItemStack(Items.STONE_AXE));
		player.getInventory().add(new ItemStack(Items.BREAD, 16));
		player.getInventory().add(new ItemStack(Items.TORCH, 16));
	}

	private static String biomeName(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
	}
}
