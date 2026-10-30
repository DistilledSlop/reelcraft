package com.reelcraft;

import com.reelcraft.client.ReelsLauncher;
import com.reelcraft.feed.ReelFeed;
import com.reelcraft.feed.ReelFeeds;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

@SuppressWarnings("UnstableApiUsage")
public class ReelcraftClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> ReelsLauncher.start(mc, mc.gui.screen()));
		context.waitFor(mc -> mc.level != null && mc.player != null && ReelFeeds.active() != null && ReelFeeds.active().current() == 0, 6000);
		context.waitTicks(40);
		context.takeScreenshot("reel-1");

		// Wait for the buffer to fill, then scroll through a few reels.
		context.waitFor(mc -> {
			ReelFeed feed = ReelFeeds.active();
			return feed != null && feed.reels().size() > ReelFeed.BUFFER_AHEAD && feed.reels().stream().skip(1).limit(ReelFeed.BUFFER_AHEAD).allMatch(r -> r.isReady());
		}, 12000);
		context.takeScreenshot("buffer-full");

		int totalLoadTicks = 0;
		for (int i = 1; i <= 10; i++) {
			int expected = i;
			int levelBefore = context.computeOnClient(mc -> System.identityHashCode(mc.level));
			context.waitTicks(12);
			// Same path as a real mouse-wheel scroll down, so the client animation runs too.
			context.runOnClient(mc -> com.reelcraft.client.ReelsClient.onWheel(-1.0));
			// Count the ticks the loading screen is up after this scroll.
			int loadTicks = 0;
			int quiet = 0;
			boolean switched = false;
			for (int t = 0; t < 600 && quiet < 5; t++) {
				context.waitTicks(1);
				boolean loading = context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen);
				switched |= context.computeOnClient(mc -> ReelFeeds.active().current() == expected);
				if (loading) {
					if (i == 2 && loadTicks == 1) {
						context.takeScreenshot("during-load");
					}
					loadTicks++;
					quiet = 0;
				} else if (switched) {
					quiet++;
					if (i == 2 && quiet == 1) {
						context.takeScreenshot("slide-start");
					}
				}
			}
			int levelAfter = context.computeOnClient(mc -> System.identityHashCode(mc.level));
			Reelcraft.LOGGER.info("CLIENTLEVEL scroll={} same={} dim={}", i, levelBefore == levelAfter, context.computeOnClient(mc -> mc.level.dimension().identifier().toString()));
			totalLoadTicks += loadTicks;
			Reelcraft.LOGGER.info("LOADTICKS scroll={} ticks={}", i, loadTicks);
			if (i <= 3 || i == 10) {
				context.waitTicks(40);
				context.takeScreenshot("reel-" + (i + 1));
			}
		}
		Reelcraft.LOGGER.info("LOADTICKS average={}", totalLoadTicks / 10.0);
		context.runOnClient(mc -> {
			ReelFeed feed = ReelFeeds.active();
			Reelcraft.LOGGER.info("FEED size={} current={}", feed.reels().size(), feed.current());
			feed.reels().forEach(r -> Reelcraft.LOGGER.info("FEED reel id={} state={} dim={} seed={} spawn={} biome={}", r.id, r.state, r.level == null ? "closed" : r.level.dimension().identifier(), r.seed, r.spawn, r.biome));
			long distinct = feed.reels().stream().map(r -> r.seed).distinct().count();
			Reelcraft.LOGGER.info("FEED distinct seeds {} of {}", distinct, feed.reels().size());
		});

		// Same close sequence the framework uses for its own singleplayer worlds: it sets the title screen itself.
		long quitStart = System.currentTimeMillis();
		context.runOnClient(mc -> {
			mc.level.disconnect(Component.translatable("menu.savingLevel"));
			mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		context.waitFor(mc -> mc.getSingleplayerServer() == null, 600);
		context.waitTicks(5);
		context.setScreen(TitleScreen::new);
		Reelcraft.LOGGER.info("QUIT OK in {} ms", System.currentTimeMillis() - quitStart);
	}
}
