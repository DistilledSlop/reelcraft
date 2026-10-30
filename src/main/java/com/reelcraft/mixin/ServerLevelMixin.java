package com.reelcraft.mixin;

import com.reelcraft.feed.ReelLevels;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	/** Vanilla returns one server-wide seed for every level; reel levels each carry their own, which is what makes them different worlds. */
	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void reelcraft$ownSeed(CallbackInfoReturnable<Long> cir) {
		Long seed = ReelLevels.seedOf(((ServerLevel) (Object) this).dimension());
		if (seed != null) {
			cir.setReturnValue(seed);
		}
	}
}
