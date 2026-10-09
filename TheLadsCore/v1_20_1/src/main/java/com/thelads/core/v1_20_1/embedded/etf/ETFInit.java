package com.thelads.core.v1_20_1.embedded.etf;

import net.fabricmc.api.ClientModInitializer;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class ETFInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() { ETF.start(); }

    /** The Lads: called by EmbeddedMods1201 while the original jar is not installed. */
    public static void init() { ETF.start(); }
}
