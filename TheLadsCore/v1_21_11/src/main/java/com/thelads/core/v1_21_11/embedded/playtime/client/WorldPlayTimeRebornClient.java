// Adapted from World Play Time Reborn 1.2.6 by KoroWin, based on World Play Time by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.playtime.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.thelads.core.v1_21_11.embedded.playtime.WorldPlayTimeReborn;
import com.thelads.core.v1_21_11.embedded.playtime.client.handler.EventHandlerCommon;

public class WorldPlayTimeRebornClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		WorldPlayTimeReborn.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> EventHandlerCommon.onClientTick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> EventHandlerCommon.onLeavingGame());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> EventHandlerCommon.onLeaveServer());
	}
}
