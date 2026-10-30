package com.reelcraft.client.mixin;

import com.reelcraft.client.ReelsClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.util.Util;

@Mixin(LevelLoadTracker.class)
public abstract class LevelLoadTrackerMixin {
	/**
	 * Vanilla keeps its loading screen up for a short delay after the level is ready, so the world can draw a few frames
	 * before it is revealed. In the feed the frozen old frame covers that, so don't wait.
	 */
	@Redirect(method = "isLevelReady", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long reelcraft$skipCloseDelay() {
		return ReelsClient.hidesLoadingScreen() ? Long.MAX_VALUE / 2 : Util.getMillis();
	}
}
