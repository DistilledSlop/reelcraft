package com.reelcraft.feed;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.reelcraft.Reelcraft;
import com.reelcraft.mixin.MinecraftServerAccessor;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.DerivedLevelData;

/** Creates and tears down the extra overworld-type levels that back each reel, each with its own random seed. */
public final class ReelLevels {
	private static final Map<ResourceKey<Level>, Long> SEEDS = new ConcurrentHashMap<>();

	private ReelLevels() {
	}

	/** The seed of a reel level, or null for any other level (which uses the server's seed as usual). */
	public static Long seedOf(ResourceKey<Level> dimension) {
		return SEEDS.get(dimension);
	}

	public static boolean isReel(ResourceKey<Level> dimension) {
		return SEEDS.containsKey(dimension);
	}

	public static void clear() {
		SEEDS.clear();
	}

	public static ServerLevel create(MinecraftServer server, long id, long seed) {
		MinecraftServerAccessor access = (MinecraftServerAccessor) server;
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Reelcraft.id("reel_" + id));
		// Must be registered before the level exists: its chunk map reads the seed while it is being constructed.
		SEEDS.put(key, seed);

		LevelStem base = server.registries().compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
		ChunkGenerator generator = base.generator();
		if (generator instanceof NoiseBasedChunkGenerator noise) {
			// A generator of its own, so no per-level state is shared between reels.
			generator = new NoiseBasedChunkGenerator(noise.getBiomeSource(), noise.generatorSettings());
		}
		LevelStem stem = new LevelStem(base.type(), generator);
		DerivedLevelData data = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());

		ServerLevel level = new ServerLevel(
			server, access.reelcraft$executor(), access.reelcraft$storageSource(), data, key, stem, false, BiomeManager.obfuscateSeed(seed), List.of(), false
		);
		// Reel levels are throwaway: never write their chunks.
		level.noSave = true;
		access.reelcraft$levels().put(key, level);
		level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
		server.getPlayerList().addWorldborderListener(level);
		return level;
	}

	public static void destroy(MinecraftServer server, ServerLevel level) {
		((MinecraftServerAccessor) server).reelcraft$levels().remove(level.dimension());
		try {
			level.close();
		} catch (IOException e) {
			Reelcraft.LOGGER.warn("Could not close reel level {}", level.dimension(), e);
		}
		SEEDS.remove(level.dimension());
	}
}
