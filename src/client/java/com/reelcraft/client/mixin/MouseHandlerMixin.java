package com.reelcraft.client.mixin;

import com.reelcraft.client.ReelsClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** In the feed the wheel scrolls reels instead of the hotbar (hotbar stays on 1-9). */
	@Inject(
		method = "onScroll",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"),
		cancellable = true
	)
	private void reelcraft$scrollFeed(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (ReelsClient.onWheel(yoffset)) {
			ci.cancel();
		}
	}
}
