package com.thelads.core.v1_20_1.embedded.emf;

import net.fabricmc.api.ClientModInitializer;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class EMFInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() { EMF.init(); }

    /** The Lads: called by EmbeddedMods1201 while the original jar is not installed. */
    public static void init() { EMF.init(); }
}
