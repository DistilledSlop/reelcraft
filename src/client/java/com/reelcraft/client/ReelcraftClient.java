package com.reelcraft.client;

import net.fabricmc.api.ClientModInitializer;

public class ReelcraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ReelsClient.init();
		ReelsHud.init();
	}
}
