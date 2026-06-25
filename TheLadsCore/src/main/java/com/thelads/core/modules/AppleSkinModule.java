package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;

public class AppleSkinModule extends Module {
    public AppleSkinModule() {
        super("AppleSkin", "Adds food saturation and exhaustion to the HUD.");
        addOption(new BoolOption("Show Saturation", true));
        addOption(new BoolOption("Show Food Values", true));
        addOption(new BoolOption("Show Exhaustion", true));
        addOption(new BoolOption("Show Saturation Overlay", true));
        addOption(new BoolOption("Show Health Overlay", true));
    }
}
