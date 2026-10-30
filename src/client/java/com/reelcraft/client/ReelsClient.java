package com.reelcraft.client;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.reelcraft.Reelcraft;
import com.reelcraft.feed.Reel;
import com.reelcraft.feed.ReelFeed;
import com.reelcraft.feed.ReelFeeds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client-side feed state: input, likes, and the scroll transition clock. */
public final class ReelsClient {
	public static final long SCROLL_COOLDOWN_MS = 450;
	public static final long TRANSITION_MS = 420;
	private static final long LOADING_HINT_MS = 2500;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Reelcraft.id("reels"));
	private static final KeyMapping NEXT = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.reelcraft.next_reel", InputConstants.Type.KEYBOARD, InputConstants.KEY_DOWN, CATEGORY)
	);
	private static final KeyMapping PREV = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.reelcraft.prev_reel", InputConstants.Type.KEYBOARD, InputConstants.KEY_UP, CATEGORY)
	);
	private static final KeyMapping LIKE = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.reelcraft.like", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY)
	);

	private static final Set<Long> liked = new HashSet<>();
	private static ReelFeed lastFeed;
	private static int lastIndex = -1;
	private static long lastScrollAt;
	private static long scrollRequestedAt;
	private static long transitionStart = Long.MIN_VALUE;
	private static int transitionDir = 1;

	private ReelsClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ReelsClient::tick);
	}

	private static void tick(Minecraft mc) {
		ReelFeed feed = ReelFeeds.active();
		if (feed != lastFeed) {
			lastFeed = feed;
			lastIndex = -1;
			liked.clear();
			transitionStart = Long.MIN_VALUE;
			// The feed runs at its own GUI scale (see WindowMixin).
			mc.resizeGui();
		}
		if (feed == null) {
			return;
		}

		int index = feed.current();
		if (lastIndex >= 0 && index != lastIndex && index >= 0) {
			transitionDir = Integer.signum(index - lastIndex);
			transitionStart = Util.getMillis();
		}
		lastIndex = index;

		while (NEXT.consumeClick()) {
			scroll(1);
		}
		while (PREV.consumeClick()) {
			scroll(-1);
		}
		while (LIKE.consumeClick()) {
			toggleLike();
		}
	}

	/** Returns true when the wheel event was consumed by the feed. */
	public static boolean onWheel(double yoffset) {
		if (ReelFeeds.active() == null || yoffset == 0) {
			return false;
		}
		scroll(yoffset < 0 ? 1 : -1);
		return true;
	}

	private static void scroll(int dir) {
		ReelFeed feed = ReelFeeds.active();
		long now = Util.getMillis();
		if (feed == null || feed.current() < 0 || now - lastScrollAt < SCROLL_COOLDOWN_MS) {
			return;
		}
		lastScrollAt = now;
		scrollRequestedAt = now;
		feed.requestScroll(dir);
	}

	private static void toggleLike() {
		Reel reel = ReelFeeds.active() == null ? null : ReelFeeds.active().currentReel();
		if (reel != null && !liked.remove(reel.id)) {
			liked.add(reel.id);
		}
	}

	public static boolean isLiked(Reel reel) {
		return liked.contains(reel.id);
	}

	/** 0..1 progress of the swipe, or 1 when idle. */
	public static float transitionProgress() {
		long elapsed = Util.getMillis() - transitionStart;
		return elapsed < 0 || elapsed >= TRANSITION_MS ? 1.0F : (float) elapsed / TRANSITION_MS;
	}

	public static int transitionDir() {
		return transitionDir;
	}

	/** True shortly after a scroll that is still waiting for the next reel to finish generating. */
	public static boolean waitingForNext() {
		ReelFeed feed = ReelFeeds.active();
		if (feed == null || Util.getMillis() - scrollRequestedAt > LOADING_HINT_MS) {
			return false;
		}
		int next = feed.current() + 1;
		return next < feed.reels().size() && !feed.reels().get(next).isReady();
	}
}
