package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class DynamicLightsModule extends Module {
    private static DynamicLightsModule INSTANCE;

    public DynamicLightsModule() {
        super("DynamicLights", "Artificially increases the block light level around the player when holding a glowing item.");
        INSTANCE = this;
    }

    public static DynamicLightsModule getInstance() {
        return INSTANCE;
    }
}
