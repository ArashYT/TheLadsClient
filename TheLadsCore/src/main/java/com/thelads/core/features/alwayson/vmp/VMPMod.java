package com.thelads.core.features.alwayson.vmp;

import com.thelads.core.features.alwayson.vmp.common.config.Config;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.NearbyEntityTracking;
import net.fabricmc.api.ModInitializer;

public class VMPMod implements ModInitializer {
    public void onInitialize() {
        if (Config.USE_OPTIMIZED_ENTITY_TRACKING) {
            NearbyEntityTracking.init();
        }
    }
}
