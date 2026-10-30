package com.reelcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.reelcraft.Reelcraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** A grab of the last frame of the reel being left, drawn sliding away while the next reel is revealed underneath. */
public final class ReelsSnapshot {
	private static final Identifier TEXTURE = Reelcraft.id("scroll_snapshot");
	/** Half resolution is plenty for something that is on screen for under half a second. */
	private static final int DOWNSCALE = 2;

	private static DynamicTexture texture;
	private static int width;
	private static int height;
	private static volatile boolean ready;
	private static boolean inFlight;
	private static int generation;

	private ReelsSnapshot() {
	}

	/**
	 * Starts an asynchronous grab of the current frame; {@link #isReady()} turns true once it is on the GPU. With
	 * {@code discardOld} the previous grab is dropped (it is of an older reel); without it, the previous grab stays usable
	 * until the new one lands, and a request is ignored while one is still in flight.
	 */
	public static void capture(boolean discardOld) {
		if (inFlight && !discardOld) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int mine = ++generation;
		if (discardOld) {
			ready = false;
		}
		inFlight = true;
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), DOWNSCALE, image -> mc.execute(() -> accept(image, mine)));
	}

	private static void accept(NativeImage image, int mine) {
		if (mine != generation) {
			// A newer grab was requested while this one was in flight.
			image.close();
			return;
		}
		inFlight = false;
		width = image.getWidth();
		height = image.getHeight();
		texture = new DynamicTexture(() -> "Reelcraft scroll snapshot", image);
		Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
		ready = true;
	}

	public static boolean isReady() {
		return ready;
	}

	/** Draws the grab over the whole window, shifted down by {@code yOffset} GUI pixels (negative moves it up). */
	public static void draw(GuiGraphicsExtractor g, int yOffset) {
		if (!ready) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, yOffset, 0, 0, g.guiWidth(), g.guiHeight(), width, height, width, height);
	}
}
