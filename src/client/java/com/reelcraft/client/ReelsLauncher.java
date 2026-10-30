package com.reelcraft.client;

import java.io.IOException;

import com.reelcraft.Reelcraft;
import com.reelcraft.feed.ReelFeeds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;

/** Creates the throwaway feed world (fresh random seed every time) and joins it. */
public final class ReelsLauncher {
	private ReelsLauncher() {
	}

	public static void start(Minecraft minecraft, Screen parent) {
		LevelStorageSource source = minecraft.getLevelSource();
		try {
			if (source.levelExists(ReelFeeds.LEVEL_ID)) {
				try (LevelStorageSource.LevelStorageAccess access = source.createAccess(ReelFeeds.LEVEL_ID)) {
					access.deleteLevel();
				}
			}
		} catch (IOException e) {
			Reelcraft.LOGGER.warn("Could not clear the previous feed world", e);
		}

		LevelSettings settings = new LevelSettings(
			ReelFeeds.LEVEL_NAME,
			GameType.SURVIVAL,
			new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
			false,
			WorldDataConfiguration.DEFAULT
		);
		minecraft.createWorldOpenFlows().createFreshLevel(
			ReelFeeds.LEVEL_ID, settings, WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions, parent
		);
	}
}
