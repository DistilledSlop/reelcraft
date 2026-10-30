package com.reelcraft.feed;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.storage.LevelData;

/**
 * Server-side feed. Every reel is its own level with its own random seed, so each one is a genuinely different world.
 * The feed keeps the current reel plus {@link #BUFFER_AHEAD} reels created, spawn-searched and chunk-generated ahead
 * of time; scrolling is just a teleport into a level that is already loaded. Reels far behind are closed.
 */
public final class ReelFeed {
	public static final int BUFFER_AHEAD = 5;
	/** Reels behind the current one that stay loaded, so scrolling back is instant too. */
	public static final int HISTORY = 2;
	/** Chunk radius generated per reel before it becomes READY. */
	public static final int PRELOAD_RADIUS = 4;
	/** Spawn candidates are sampled this far (blocks) around the centre of the reel's own region. */
	private static final int SPAWN_SPREAD = 1500;
	/**
	 * Every reel gets its own region of the coordinate space, far from the previous ones. The client keeps one level for
	 * the whole feed, so this guarantees a new reel never lands on stale chunks left over from an earlier one.
	 */
	private static final int REGION_SPACING = 40_000;
	private static final int REGION_COUNT = 700;
	private static final int SPAWN_CANDIDATES = 64;
	/** A new reel avoids the biomes of this many reels before it, when it can. */
	private static final int VARIETY_WINDOW = 4;
	/** Creating a level hitches the server a little, so space them out. */
	private static final int CREATE_COOLDOWN_TICKS = 10;
	/**
	 * Startup: let the first reel's own terrain finish streaming in before the buffer starts competing for the same
	 * worker threads, then fill it a couple of worlds at a time instead of all at once.
	 */
	private static final int START_DELAY_TICKS = 120;
	private static final int MAX_GENERATING = 2;

	private final MinecraftServer server;
	private final RandomSource random = RandomSource.create();
	private final List<Reel> reels = new CopyOnWriteArrayList<>();
	private volatile int current = -1;
	private int pendingScroll;
	private int createCooldown;
	private int ticksSinceBegin;
	private long nextId;
	private volatile boolean stopping;
	/** Retired levels waiting to finish unloading their chunks, with how many ticks they have waited. */
	private final Map<ServerLevel, Integer> closing = new HashMap<>();
	/** Close a retired level anyway after this long, in case a chunk refuses to unload. */
	private static final int CLOSE_TIMEOUT_TICKS = 20 * 30;

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

	/** Called when the player joins: the world spawn they landed on (the server's own world) becomes reel #1. */
	public void begin(ServerPlayer player) {
		if (!this.reels.isEmpty()) {
			return;
		}
		ServerLevel level = this.server.overworld();
		Reel first = new Reel(this.nextId++, this.random.nextLong());
		first.level = level;
		first.seed = level.getSeed();
		first.chunk = player.chunkPosition();
		first.spawn = player.blockPosition();
		first.biome = biomeName(level, first.spawn);
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
		for (Reel reel : this.reels) {
			this.releaseChunks(reel);
		}
	}

	public void tick() {
		if (this.stopping || this.current < 0) {
			return;
		}
		this.closeRetired();
		this.ticksSinceBegin++;
		if (this.createCooldown > 0) {
			this.createCooldown--;
		}
		int ahead = this.reels.size() - 1 - this.current;
		boolean warmedUp = this.ticksSinceBegin >= START_DELAY_TICKS;
		if (ahead < BUFFER_AHEAD && this.createCooldown == 0 && warmedUp && this.generating() < MAX_GENERATING) {
			this.createCooldown = CREATE_COOLDOWN_TICKS;
			this.startReel();
		}
		if (this.pendingScroll != 0) {
			int target = this.current + this.pendingScroll;
			if (target < 0 || target >= this.reels.size() || this.reels.get(target).level == null && target < this.current) {
				// Nothing before the first reel, and reels that have been closed are gone for good.
				if (target < this.current) {
					this.pendingScroll = 0;
				}
			}
			if (target >= 0 && target < this.reels.size() && this.reels.get(target).isReady()) {
				this.pendingScroll = 0;
				this.moveTo(target);
			}
			// Otherwise leave a forward request pending until the next reel finishes generating.
		}
	}

	/** Reels that are still being created (searching or generating chunks). */
	private int generating() {
		int count = 0;
		for (Reel reel : this.reels) {
			if (reel.level != null && !reel.isReady()) {
				count++;
			}
		}
		return count;
	}

	private void moveTo(int index) {
		ServerPlayer player = this.server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
		if (player == null) {
			return;
		}
		this.current = index;
		this.enter(player, this.reels.get(index));
		this.retireOld();
	}

	/**
	 * Retire the levels of reels that are too far behind to scroll back to. Closing a level that still has hundreds of
	 * chunks loaded blocks the server thread for about half a second, and the player's respawn packet is sent at the end
	 * of that same tick, so scrolls would hang. Instead the chunks are released here and unload over the next ticks, and
	 * {@link #closeRetired} closes the level once it is empty.
	 */
	private void retireOld() {
		for (int i = 0; i < this.current - HISTORY; i++) {
			Reel old = this.reels.get(i);
			ServerLevel level = old.level;
			if (level != null && level != this.server.overworld()) {
				this.releaseChunks(old);
				old.level = null;
				this.closing.put(level, 0);
			}
		}
	}

	/** Close at most one fully unloaded retired level per tick. */
	private void closeRetired() {
		Iterator<Map.Entry<ServerLevel, Integer>> it = this.closing.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<ServerLevel, Integer> entry = it.next();
			ServerLevel level = entry.getKey();
			int waited = entry.setValue(entry.getValue() + 1);
			if (level.getChunkSource().getLoadedChunksCount() == 0 || waited > CLOSE_TIMEOUT_TICKS) {
				it.remove();
				ReelLevels.destroy(this.server, level);
				return;
			}
		}
	}

	private void releaseChunks(Reel reel) {
		ServerLevel level = reel.level;
		if (level != null && reel.chunk != null) {
			level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, reel.chunk, PRELOAD_RADIUS);
			reel.chunk = null;
		}
	}

	/** Step 1: create a brand-new world (random seed) for the next reel and pick where in it to spawn. */
	private void startReel() {
		Reel reel = new Reel(this.nextId++, this.random.nextLong());
		reel.seed = this.random.nextLong();
		this.reels.add(reel);
		ServerLevel level = ReelLevels.create(this.server, reel.id, reel.seed);
		reel.level = level;
		BlockPos spot = this.pickSpawn(reel, level);
		reel.chunk = ChunkPos.containing(spot);
		reel.biome = biomeAt(resolver(level), spot);
		reel.state = Reel.State.GENERATING;
		this.generate(reel, level);
	}

	/**
	 * Samples the new world's terrain generator (no chunks loaded) for a spot that is dry land, preferring a biome that
	 * none of the last few reels used.
	 */
	private BlockPos pickSpawn(Reel reel, ServerLevel level) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		BiomeResolver resolver = resolver(level);
		BlockPos fallback = null;
		for (int i = 0; i < SPAWN_CANDIDATES; i++) {
			int centerX = (int) ((reel.id % REGION_COUNT) - REGION_COUNT / 2) * REGION_SPACING;
			int x = centerX + this.random.nextInt(-SPAWN_SPREAD, SPAWN_SPREAD);
			int z = this.random.nextInt(-SPAWN_SPREAD, SPAWN_SPREAD);
			int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
			if (y <= level.getSeaLevel() + 1) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			if (fallback == null) {
				fallback = pos;
			}
			if (!this.recentlySeen(reel, biomeAt(resolver, pos))) {
				return pos;
			}
		}
		return fallback != null ? fallback : new BlockPos((int) ((reel.id % REGION_COUNT) - REGION_COUNT / 2) * REGION_SPACING, level.getSeaLevel() + 1, 0);
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

	/** Step 2: generate the surroundings so the player never waits on terrain when they scroll in. */
	private void generate(Reel reel, ServerLevel level) {
		ChunkPos pos = reel.chunk;
		level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, pos, PRELOAD_RADIUS).thenRunAsync(() -> {
			if (this.stopping || reel.level != level) {
				return;
			}
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getMiddleBlockPosition(0));
			reel.spawn = surface;
			reel.biome = biomeName(level, surface);
			reel.state = Reel.State.READY;
			Reelcraft.LOGGER.debug("Reel {} ready: seed {} at {} ({})", reel.id, reel.seed, reel.spawn, reel.biome);
		}, this.server);
	}

	/** Drop the player into a reel as a fresh run. */
	private void enter(ServerPlayer player, Reel reel) {
		ServerLevel level = reel.level;
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

	/** A biome lookup straight from the generator's climate noise: works before any chunk exists. */
	private static BiomeResolver resolver(ServerLevel level) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		if (generator.getBiomeSource() instanceof MultiNoiseBiomeSource multiNoise) {
			return multiNoise.createResolver(randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
		}
		return null;
	}

	private static String biomeAt(BiomeResolver resolver, BlockPos pos) {
		if (resolver == null) {
			return "unknown";
		}
		Holder<Biome> biome = resolver.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
	}
}
