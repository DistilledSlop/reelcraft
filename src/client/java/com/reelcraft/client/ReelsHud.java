package com.reelcraft.client;

import java.util.List;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.reelcraft.Reelcraft;
import com.reelcraft.feed.Reel;
import com.reelcraft.feed.ReelFeed;
import com.reelcraft.feed.ReelFeeds;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Instagram Reels chrome, Minecraftified: a 9:16 column pillarboxed over the world, with the action rail on the
 * right, creator + caption bottom-left, a buffer readout of upcoming reels, and the bottom nav bar.
 */
public final class ReelsHud {
	private static final int NAV_H = 24;
	private static final int ICON = 16;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int DIM = 0xFFA8A8A8;
	private static final int LIKE_RED = 0xFFFF3040;
	private static final int PILLAR = 0xFF000000;
	private static final int CURTAIN = 0xFF101010;
	private static final RenderPipeline GUI = RenderPipelines.GUI_TEXTURED;

	private static final List<Identifier> SHIFTED = List.of(
		VanillaHudElements.HOTBAR,
		VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR,
		VanillaHudElements.FOOD_BAR,
		VanillaHudElements.AIR_BAR,
		VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR,
		VanillaHudElements.EXPERIENCE_LEVEL,
		VanillaHudElements.INFO_BAR_FOREGROUND,
		VanillaHudElements.HELD_ITEM_TOOLTIP
	);

	private ReelsHud() {
	}

	private static Identifier sprite(String name) {
		return Reelcraft.id("reels/" + name);
	}

	public static void init() {
		// Lift the vanilla status HUD above the nav bar and keep chat inside the column.
		for (Identifier element : SHIFTED) {
			HudElementRegistry.replaceElement(element, original -> shifted(original, false));
		}
		HudElementRegistry.replaceElement(VanillaHudElements.CHAT, original -> shifted(original, true));
		HudElementRegistry.addLast(Reelcraft.id("reels_overlay"), ReelsHud::extract);
	}

	private static HudElement shifted(HudElement original, boolean chat) {
		return (graphics, delta) -> {
			if (ReelFeeds.active() == null) {
				original.extractRenderState(graphics, delta);
				return;
			}
			int dx = chat ? columnX(graphics) : 0;
			graphics.pose().pushMatrix();
			graphics.pose().translate(dx, -NAV_H);
			original.extractRenderState(graphics, delta);
			graphics.pose().popMatrix();
		};
	}

	private static int columnWidth(GuiGraphicsExtractor g) {
		return Math.min(g.guiWidth(), g.guiHeight() * 9 / 16);
	}

	private static int columnX(GuiGraphicsExtractor g) {
		return (g.guiWidth() - columnWidth(g)) / 2;
	}

	private static void icon(GuiGraphicsExtractor g, String name, int x, int y, int color) {
		g.blitSprite(GUI, sprite(name), x, y, ICON, ICON, color);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		ReelFeed feed = ReelFeeds.active();
		Reel reel = feed == null ? null : feed.currentReel();
		if (reel == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cw = columnWidth(g);
		int x0 = columnX(g);
		int x1 = x0 + cw;

		// Pillarbox: the "video" is the portrait column.
		if (x0 > 0) {
			g.fill(0, 0, x0, h, PILLAR);
			g.fill(x1, 0, w, h, PILLAR);
		}

		topBar(g, font, feed, x0, cw);
		rail(g, font, reel, x1);
		caption(g, font, reel, x0, h);
		navBar(g, mc, x0, x1, h);
		transition(g, font, x0, x1, h);

		if (ReelsClient.waitingForNext()) {
			g.centeredText(font, Component.translatable("reelcraft.loading_next"), w / 2, h / 2, WHITE);
		}
	}

	private static void topBar(GuiGraphicsExtractor g, Font font, ReelFeed feed, int x0, int cw) {
		g.fillGradient(x0, 0, x0 + cw, 34, 0xAA000000, 0x00000000);
		g.text(font, Component.literal("Reels").withStyle(ChatFormatting.BOLD), x0 + 8, 8, WHITE);
		icon(g, "camera", x0 + cw - ICON - 8, 6, WHITE);

		// Buffer readout: one pip per upcoming reel, green once it has finished generating.
		int pipW = 10;
		int total = ReelFeed.BUFFER_AHEAD;
		int px = x0 + cw / 2 - (total * (pipW + 2)) / 2;
		List<Reel> reels = feed.reels();
		for (int i = 1; i <= total; i++) {
			int idx = feed.current() + i;
			int color = 0xFF3A3A3A;
			if (idx < reels.size()) {
				color = switch (reels.get(idx).state) {
					case READY -> 0xFF55FF55;
					case GENERATING -> 0xFFFFD84A;
					case SEARCHING -> 0xFF8A8A8A;
				};
			}
			g.fill(px, 22, px + pipW, 24, color);
			px += pipW + 2;
		}
	}

	private static void rail(GuiGraphicsExtractor g, Font font, Reel reel, int x1) {
		int h = g.guiHeight();
		int cx = x1 - 8 - ICON / 2;
		// Flow the rail between the top bar and the caption block, tightening spacing on small windows.
		int top = 40;
		int limit = captionTop(h) - 8;
		int step = Mth.clamp((limit - top) / 5, 22, 36);
		boolean counts = step >= 30;
		boolean liked = ReelsClient.isLiked(reel);

		int y = top;
		icon(g, liked ? "heart" : "heart_outline", cx - ICON / 2, y, liked ? LIKE_RED : WHITE);
		if (counts) {
			count(g, font, Reel.compact(reel.likes() + (liked ? 1 : 0)), cx, y + ICON + 2);
		}
		y += step;
		icon(g, "comment", cx - ICON / 2, y, WHITE);
		if (counts) {
			count(g, font, Reel.compact(reel.comments()), cx, y + ICON + 2);
		}
		y += step;
		icon(g, "share", cx - ICON / 2, y, WHITE);
		if (counts) {
			count(g, font, Reel.compact(reel.shares()), cx, y + ICON + 2);
		}
		y += step;
		icon(g, "more", cx - ICON / 2, y, WHITE);
		y += step;

		// Audio thumbnail: a music disc that spins, framed like the rounded square on Reels.
		g.fill(cx - 10, y - 2, cx + 10, y + 18, WHITE);
		g.fill(cx - 9, y - 1, cx + 9, y + 17, 0xFF1B1B1B);
		g.pose().pushMatrix();
		g.pose().translate(cx, y + 8);
		g.pose().rotate((Util.getMillis() % 4000L) / 4000.0F * Mth.TWO_PI);
		g.blitSprite(GUI, sprite("music_disc"), -ICON / 2, -ICON / 2, ICON, ICON, WHITE);
		g.pose().popMatrix();
	}

	/** Top of the creator row; sits above the (lifted) vanilla hotbar and status bars. */
	private static int captionTop(int h) {
		return h - NAV_H - 22 - 28 - 46;
	}

	private static void count(GuiGraphicsExtractor g, Font font, String text, int cx, int y) {
		g.centeredText(font, text, cx, y, WHITE);
	}

	private static void caption(GuiGraphicsExtractor g, Font font, Reel reel, int x0, int h) {
		// Sits above the (lifted) vanilla hotbar and status bars.
		int x = x0 + 8;
		int bottom = h - NAV_H - 22 - 28;
		g.fillGradient(x0, bottom - 70, x0 + columnWidth(g), bottom + 6, 0x00000000, 0x88000000);

		int y = captionTop(h);
		// Avatar + handle + verified + follow.
		g.fill(x, y, x + 18, y + 18, WHITE);
		g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF000000 | (int) (0x303030 + (reel.id * 0x2F5A3L) % 0x707070));
		g.blitSprite(GUI, sprite("profile"), x + 1, y + 1, 16, 16, WHITE);
		Component handle = Component.literal(font.plainSubstrByWidth(reel.handle(), columnWidth(g) - 24 - 16 - 34)).withStyle(ChatFormatting.BOLD);
		g.text(font, handle, x + 24, y + 5, WHITE);
		int hx = x + 24 + font.width(handle) + 4;
		g.blitSprite(GUI, sprite("verified"), hx, y + 3, 12, 12, 0xFF3797F0);
		int fx = hx + 18;
		int fw = font.width("Follow") + 10;
		if (fx + fw < x0 + columnWidth(g) - 36) {
			g.outline(fx, y + 2, fw, 14, WHITE);
			g.text(font, "Follow", fx + 5, y + 5, WHITE);
		}

		// Caption line.
		y += 24;
		g.text(font, reel.caption(), x, y, WHITE);

		// Audio ticker.
		y += 12;
		icon(g, "note", x - 2, y - 4, WHITE);
		String audio = "Original audio · " + reel.biome.replace('_', ' ');
		g.text(font, audio, x + 14, y, WHITE);
	}

	private static void navBar(GuiGraphicsExtractor g, Minecraft mc, int x0, int x1, int h) {
		int top = h - NAV_H;
		g.fill(x0, top, x1, h, 0xFF000000);
		g.fill(x0, top, x1, top + 1, 0xFF262626);
		String[] icons = {"home", "search", "reels", "shop", "profile"};
		int slot = (x1 - x0) / icons.length;
		for (int i = 0; i < icons.length; i++) {
			int cx = x0 + slot * i + slot / 2;
			icon(g, icons[i], cx - ICON / 2, top + (NAV_H - ICON) / 2, i == 2 ? WHITE : DIM);
		}
	}

	/** The full-window curtain shown while a scroll's level change loads; the swipe in {@link #transition} slides it away. */
	public static void extractCover(GuiGraphicsExtractor g) {
		Font font = Minecraft.getInstance().font;
		int x0 = columnX(g);
		int x1 = x0 + columnWidth(g);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), PILLAR);
		g.fill(x0, 0, x1, g.guiHeight(), CURTAIN);
		g.centeredText(font, Component.literal("Reels").withStyle(ChatFormatting.BOLD), (x0 + x1) / 2, g.guiHeight() / 2 - 4, WHITE);
	}

	/** Covers the teleport with a vertical swipe, like the previous reel sliding out of view. */
	private static void transition(GuiGraphicsExtractor g, Font font, int x0, int x1, int h) {
		float p = ReelsClient.transitionProgress();
		if (p >= 1.0F) {
			return;
		}
		float eased = 1.0F - (1.0F - p) * (1.0F - p);
		int dir = ReelsClient.transitionDir();
		// Scrolling down pushes the old reel up; scrolling up pulls it down.
		int offset = Math.round(eased * h) * (dir > 0 ? -1 : 1);
		int top = offset;
		g.enableScissor(x0, 0, x1, h - NAV_H);
		g.fill(x0, top, x1, top + h, CURTAIN);
		g.centeredText(font, Component.literal("Reels").withStyle(ChatFormatting.BOLD), (x0 + x1) / 2, top + h / 2 - 4, WHITE);
		g.disableScissor();
	}
}
