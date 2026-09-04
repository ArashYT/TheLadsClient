package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;

public class EnhancedToolbarsModule extends Module {
    public EnhancedToolbarsModule() {
        super("EnhancedToolbars", "Better item tooltips.");
        addOption(new BoolOption("Detailed Durability", true));
        addOption(new BoolOption("Show Max Durability", true));
        addOption(new BoolOption("Colorize Durability", true));
        addOption(new BoolOption("Show Item Attributes", true));
    }
}
