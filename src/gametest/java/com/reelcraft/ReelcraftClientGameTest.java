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

		for (int i = 2; i <= 3; i++) {
			int expected = i - 1;
			context.runOnClient(mc -> ReelFeeds.active().requestScroll(1));
			context.waitFor(mc -> ReelFeeds.active().current() == expected, 600);
			context.waitTicks(5);
			context.takeScreenshot("reel-" + i + "-swipe");
			context.waitTicks(60);
			context.takeScreenshot("reel-" + i);
		}

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
