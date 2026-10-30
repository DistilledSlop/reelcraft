package com.reelcraft.mixin;

import com.reelcraft.feed.ReelFeeds;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	/** The feed world is thrown away on the next launch, so never spend time (or disk) saving it. */
	@Inject(method = "saveAllChunks", at = @At("HEAD"), cancellable = true)
	private void reelcraft$skipSavingFeed(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
		if (ReelFeeds.active() != null) {
			cir.setReturnValue(false);
		}
	}
}
