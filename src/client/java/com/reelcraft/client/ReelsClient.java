package com.reelcraft.client;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.reelcraft.Reelcraft;
import com.reelcraft.feed.Reel;
import com.reelcraft.feed.ReelFeed;
import com.reelcraft.feed.ReelFeeds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

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
	/** The first reel has been shown, so later level loads are scrolls (the initial world load keeps vanilla's screen). */
	private static boolean feedStarted;
	private static boolean loading;
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
			feedStarted = false;
			loading = false;
			// The feed runs at its own GUI scale (see WindowMixin).
			mc.resizeGui();
		}
		if (feed == null) {
			return;
		}

		boolean loadingScreen = mc.gui.screen() instanceof LevelLoadingScreen;
		if (!loadingScreen && feed.current() >= 0 && mc.level != null) {
			feedStarted = true;
		}
		boolean nowLoading = feedStarted && loadingScreen;
		if (loading && !nowLoading) {
			// The new reel is ready: slide the curtain away.
			transitionStart = Util.getMillis();
		}
		loading = nowLoading;

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
		if (loading) {
			return 0.0F;
		}
		long elapsed = Util.getMillis() - transitionStart;
		return elapsed < 0 || elapsed >= TRANSITION_MS ? 1.0F : (float) elapsed / TRANSITION_MS;
	}

	/** True while a scroll's level change is loading and the feed is drawing its own curtain instead of vanilla's screen. */
	public static boolean hidesLoadingScreen() {
		return feedStarted && ReelFeeds.active() != null;
	}

	public static int transitionDir() {
		return transitionDir;
	}

	/**
	 * Sideways view-space shift that moves a first-person hand from the edge of the full window into the 9:16 column.
	 * Hands sit about 0.6 units off-centre; the column is {@code ratio} of the window width, so scale that offset by it. Pulling further than this puts the item near the centre of the view.
	 */
	public static float handShift(InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if (ReelFeeds.active() == null || mc.player == null) {
			return 0.0F;
		}
		Window window = mc.getWindow();
		float ratio = Math.min(window.getWidth(), window.getHeight() * 9 / 16.0F) / window.getWidth();
		HumanoidArm mainArm = mc.player.getMainArm();
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mainArm : (mainArm == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		return -side * (1.0F - ratio) * 0.6F;
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
