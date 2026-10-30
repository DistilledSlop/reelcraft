package com.reelcraft.client.mixin;

import com.reelcraft.client.ReelsClient;
import com.reelcraft.client.ReelsHud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	/**
	 * Every reel is its own level, so scrolling triggers a level change and vanilla's "Loading terrain" screen. Between
	 * reels the feed draws its own swipe curtain instead; the screen still runs, so the load handshake is unchanged.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void reelcraft$hideState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (ReelsClient.hidesLoadingScreen()) {
			// The HUD is not drawn under a screen, so cover the gap with the feed's own curtain.
			ReelsHud.extractCover(graphics);
			ci.cancel();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void reelcraft$hideBackground(CallbackInfo ci) {
		if (ReelsClient.hidesLoadingScreen()) {
			ci.cancel();
		}
	}
}
