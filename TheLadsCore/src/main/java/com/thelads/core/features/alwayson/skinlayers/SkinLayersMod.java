package com.thelads.core.features.alwayson.skinlayers;

import net.fabricmc.api.ClientModInitializer;

public class SkinLayersMod extends SkinLayersModBase implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        this.onInitialize();
    }
}
