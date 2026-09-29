package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;

public class EnhancedTooltipsModule extends Module {
    public EnhancedTooltipsModule() {
        super("EnhancedTooltips", "Show item identifiers, food values and component details in tooltips.");
        addOption(new BoolOption("Show Item ID", true));
        addOption(new BoolOption("Show Food Values", true));
        addOption(new BoolOption("Show Component Count", false));
        addOption(new BoolOption("Show NBT Tags", false));
    }
}
