package com.reelcraft.feed;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;

/** Owns the single active {@link ReelFeed}; only exists while the hidden feed world is the running server. */
public final class ReelFeeds {
	/** Level folder and display name of the throwaway feed world. */
	public static final String LEVEL_ID = "reelcraft_feed";
	public static final String LEVEL_NAME = "Reelcraft";

	private static volatile ReelFeed active;

	private ReelFeeds() {
	}

	public static ReelFeed active() {
		return active;
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			active = isFeedWorld(server) ? new ReelFeed(server) : null;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (active != null) {
				active.shutdown();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (active != null) {
				active.begin(handler.getPlayer());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (active != null) {
				active.tick();
			}
		});
	}

	private static boolean isFeedWorld(MinecraftServer server) {
		return LEVEL_NAME.equals(server.getWorldData().getLevelName());
	}
}
