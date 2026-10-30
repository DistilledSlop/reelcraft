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

/** Client-side feed state: input and likes. */
public final class ReelsClient {
	public static final long SCROLL_COOLDOWN_MS = 450;
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
	private static long lastScrollAt;
	private static long scrollRequestedAt;
	public static final long SLIDE_MS = 320;
	/** How long to wait for the new level before sliding anyway, so a missed signal can never leave a frozen frame. */
	private static final long HOLD_TIMEOUT_MS = 3000;
	private static final long RECAPTURE_MS = 250;
	/** Give up waiting for the frame grab and scroll anyway. */
	private static final long CAPTURE_TIMEOUT_MS = 300;

	private enum Phase { IDLE, WAITING, HOLD, SLIDE }

	private static Phase phase = Phase.IDLE;
	private static int scrollDir = 1;
	private static int indexAtScroll = -1;
	private static long phaseStart;
	private static long lastCapture;
	private static boolean sawLoading;
	private static boolean requestSent;
	/** The first reel has been shown, so later level loads are scrolls (the initial world load keeps vanilla's screen). */
	private static boolean feedStarted;

	private ReelsClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ReelsClient::tick);
	}

	private static void tick(Minecraft mc) {
		ReelFeed feed = ReelFeeds.active();
		if (feed != lastFeed) {
			lastFeed = feed;
			liked.clear();
			feedStarted = false;
			phase = Phase.IDLE;
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
		tickSlide(feed, loadingScreen);


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
		scrollDir = dir;
		indexAtScroll = feed.current();
		phase = Phase.WAITING;
		phaseStart = now;
		lastCapture = now;
		sawLoading = false;
		// The server is only asked to move once the grab of this reel is ready: the next world is preloaded, so it would
		// otherwise arrive before the frozen frame exists and the empty sky would show first.
		requestSent = false;
		ReelsSnapshot.capture(true);
	}

	/**
	 * Drives the scroll animation: grab the old reel, freeze it over the screen once the server has moved us, then slide
	 * it away when the new reel's level has arrived.
	 */
	private static void tickSlide(ReelFeed feed, boolean loadingScreen) {
		long now = Util.getMillis();
		switch (phase) {
			case WAITING -> {
				if (!requestSent) {
					if (ReelsSnapshot.isReady() || now - phaseStart > CAPTURE_TIMEOUT_MS) {
						requestSent = true;
						lastCapture = now;
						feed.requestScroll(scrollDir);
					}
				} else if (feed.current() != indexAtScroll) {
					phase = Phase.HOLD;
					phaseStart = now;
				} else if (now - lastCapture > RECAPTURE_MS) {
					// The next reel is not ready yet and play continues: keep the grab fresh.
					lastCapture = now;
					ReelsSnapshot.capture(false);
				}
			}
			case HOLD -> {
				sawLoading |= loadingScreen;
				boolean arrived = !loadingScreen && (sawLoading || now - phaseStart > 400);
				if ((arrived && ReelsSnapshot.isReady()) || now - phaseStart > HOLD_TIMEOUT_MS) {
					phase = Phase.SLIDE;
					phaseStart = now;
				}
			}
			case SLIDE -> {
				if (now - phaseStart >= SLIDE_MS) {
					phase = Phase.IDLE;
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Vertical offset (GUI pixels) at which to draw the frozen old reel, or null when nothing should be drawn. Scrolling
	 * down pushes the old reel up and off; scrolling up pulls it down.
	 */
	public static Integer snapshotOffset(int guiHeight) {
		if (phase == Phase.HOLD) {
			return 0;
		}
		if (phase == Phase.SLIDE) {
			float t = Math.min(1.0F, (Util.getMillis() - phaseStart) / (float) SLIDE_MS);
			float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
			return Math.round(eased * guiHeight) * (scrollDir > 0 ? -1 : 1);
		}
		return null;
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

	/** True once the feed is running: later level changes are scrolls, so vanilla's loading screen is not drawn. */
	public static boolean hidesLoadingScreen() {
		return feedStarted && ReelFeeds.active() != null;
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
