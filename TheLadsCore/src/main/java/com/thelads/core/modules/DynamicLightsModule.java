package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.BoolOption;

public class DynamicLightsModule extends Module {
    private static DynamicLightsModule INSTANCE;

    public DynamicLightsModule() {
        super("DynamicLights", "Artificially increases the block light level around the player when holding a glowing item.");
        addOption(new SliderOption("Light Radius", 15.0, 5.0, 30.0, 1.0));
        addOption(new DropdownOption("Quality", 0, "Fast", "Fancy"));
        addOption(new BoolOption("Entities", true));
        addOption(new BoolOption("Dropped Items", true));
        addOption(new BoolOption("Underwater", true));
        INSTANCE = this;
    }

    public static DynamicLightsModule getInstance() {
        return INSTANCE;
    }
}
