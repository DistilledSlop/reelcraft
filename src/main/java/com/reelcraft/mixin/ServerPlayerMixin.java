package com.reelcraft.mixin;

import com.reelcraft.feed.ReelLevels;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	/**
	 * Reels are separate levels on the server, but the client is told it is always in the overworld. A respawn into the
	 * dimension the client is already in keeps its client level and renderer instead of rebuilding them, which is most
	 * of the wait when scrolling.
	 */
	@Redirect(
		method = "createCommonSpawnInfo",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;")
	)
	private ResourceKey<Level> reelcraft$clientSeesOverworld(ServerLevel level) {
		ResourceKey<Level> key = level.dimension();
		return ReelLevels.isReel(key) ? Level.OVERWORLD : key;
	}
}
