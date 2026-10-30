package com.reelcraft.mixin;

import com.reelcraft.feed.ReelLevels;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@Shadow
	@Final
	private ServerLevel level;

	/**
	 * Reel levels are throwaway, so their chunks are never written. They can't use {@code noSave} for that: a chunk map
	 * only unloads chunks while a level is saveable, so such a level would keep every chunk it ever loaded.
	 */
	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void reelcraft$neverWriteReelChunks(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		if (ReelLevels.isReel(this.level.dimension())) {
			cir.setReturnValue(false);
		}
	}
}
