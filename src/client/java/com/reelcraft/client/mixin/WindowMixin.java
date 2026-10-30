package com.reelcraft.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.reelcraft.feed.ReelFeeds;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {
	@Shadow
	public abstract int getHeight();

	/** The 9:16 column is only as wide as 9/16 of the GUI height, so the feed needs a smaller GUI scale than vanilla's auto. */
	@Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true)
	private void reelcraft$feedScale(int guiScale, boolean forceUnicode, CallbackInfoReturnable<Integer> cir) {
		if (ReelFeeds.active() != null) {
			cir.setReturnValue(Math.max(1, this.getHeight() / 480));
		}
	}
}
