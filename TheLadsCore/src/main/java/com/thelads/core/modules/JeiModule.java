package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;

public class JeiModule extends Module {
    public JeiModule() {
        super("JEI (Just Enough Items)", "Lightweight native recipe viewer.");
        addOption(new BoolOption("Show Item IDs", false));
        addOption(new BoolOption("Cheat Mode", false));
        addOption(new BoolOption("Center Search Bar", true));
        addOption(new BoolOption("Show Recipes", true));
    }
}
