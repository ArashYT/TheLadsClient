// Adapted from World Play Time 1.2.2 by Khajiitos (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.playtime.fabric;

import com.thelads.core.v1_21_1.embedded.playtime.WorldPlayTime;
import com.thelads.core.v1_21_1.embedded.playtime.handler.EventHandlerCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class WorldPlayTimeFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldPlayTime.init();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> EventHandlerCommon.onClientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> EventHandlerCommon.onLeavingGame());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> EventHandlerCommon.onLeaveServer());
    }
}
