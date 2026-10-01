package com.thelads.core.v26_2.embedded.emf;

import net.fabricmc.api.ClientModInitializer;

@net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
public class EMFInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() { EMF.init(); }



}
