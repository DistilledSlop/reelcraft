package com.reelcraft.client.mixin;

import com.reelcraft.client.ReelsClient;
import com.reelcraft.client.ReelsHud;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
	protected LevelLoadingScreenMixin(Component title) {
		super(title);
	}

	/**
	 * Every reel is its own level, so a scroll triggers a level change and vanilla's "Loading terrain" screen. The next
	 * world is already generated, so the feed shows nothing extra: the screen still runs (the load handshake is
	 * unchanged) but draws only the feed's own UI, since the HUD is not drawn under a screen.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void reelcraft$hideState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (ReelsClient.hidesLoadingScreen()) {
			ReelsHud.extractFeed(graphics);
			ci.cancel();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void reelcraft$hideBackground(CallbackInfo ci) {
		if (ReelsClient.hidesLoadingScreen()) {
			ci.cancel();
		}
	}

	/** No menu blur over the world while a scroll loads. */
	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
		if (!ReelsClient.hidesLoadingScreen()) {
			super.extractBlurredBackground(graphics);
		}
	}
}
