package com.thelads.core.features.alwayson.raisesoundlimit;

import net.fabricmc.api.ClientModInitializer;

public class RSLSMod implements ClientModInitializer {

    public void onInitializeClient() {
        RSLSConfig.init();
        RSLSInjectorFFM.init();
    }
}
