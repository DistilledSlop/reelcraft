package com.reelcraft.mixin;

import java.util.Map;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
	@Accessor("levels")
	Map<ResourceKey<Level>, ServerLevel> reelcraft$levels();

	@Accessor("executor")
	Executor reelcraft$executor();

	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess reelcraft$storageSource();
}
