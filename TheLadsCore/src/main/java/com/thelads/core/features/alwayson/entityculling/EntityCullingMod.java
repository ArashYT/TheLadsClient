package com.thelads.core.features.alwayson.entityculling;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class EntityCullingMod extends EntityCullingModBase implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        super.onInitialize();
        ClientTickEvents.START_CLIENT_TICK.register(client -> this.clientTick());
    }
}
