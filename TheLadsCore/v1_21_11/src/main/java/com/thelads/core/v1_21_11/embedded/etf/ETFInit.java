package com.thelads.core.v1_21_11.embedded.etf;

import net.fabricmc.api.ClientModInitializer;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class ETFInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() { ETF.start(); }



}
