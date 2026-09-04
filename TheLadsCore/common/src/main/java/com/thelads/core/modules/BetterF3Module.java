package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class BetterF3Module extends Module {
    public BetterF3Module() {
        super("BetterF3", "Customizable and colorful F3 debug screen.");
        addOption(new BoolOption("Rainbow Colors", false));
        addOption(new BoolOption("Hide Inessential", true));
    }
}
