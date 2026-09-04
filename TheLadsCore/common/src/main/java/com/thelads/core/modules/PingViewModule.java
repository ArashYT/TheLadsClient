package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

public class PingViewModule extends Module {
    public PingViewModule() {
        super("PingView", "Show numerical ping in player tab list.");
        addOption(new BoolOption("Show Numbers", true));
        addOption(new DropdownOption("Color Mode", 0, "Latency", "Static"));
    }
}
