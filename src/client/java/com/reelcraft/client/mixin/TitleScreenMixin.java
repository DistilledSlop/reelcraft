package com.reelcraft.client.mixin;

import java.util.List;

import com.reelcraft.client.ReelsLauncher;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	/** Singleplayer opens the feed instead of the world list (Shift-click keeps the classic list). */
	@Inject(method = "init", at = @At("TAIL"))
	private void reelcraft$replaceSingleplayer(CallbackInfo ci) {
		for (GuiEventListener child : List.copyOf(this.children())) {
			if (child instanceof Button button
				&& button.getMessage().getContents() instanceof TranslatableContents contents
				&& contents.getKey().equals("menu.singleplayer")) {
				Button replacement = Button.builder(button.getMessage(), b -> {
						if (this.minecraft.hasShiftDown()) {
							this.minecraft.gui.setScreen(new SelectWorldScreen(this));
						} else {
							ReelsLauncher.start(this.minecraft, this);
						}
					})
					.bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight())
					.tooltip(Tooltip.create(Component.translatable("reelcraft.singleplayer.tooltip")))
					.build();
				this.removeWidget(button);
				this.addRenderableWidget(replacement);
			}
		}
	}
}
