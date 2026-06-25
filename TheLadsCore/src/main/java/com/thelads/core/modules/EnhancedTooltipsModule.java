package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;

public class EnhancedTooltipsModule extends Module {
    public EnhancedTooltipsModule() {
        super("EnhancedTooltips", "Better item tooltips.");
        addOption(new BoolOption("Show NBT Tags", false));
    }
}
