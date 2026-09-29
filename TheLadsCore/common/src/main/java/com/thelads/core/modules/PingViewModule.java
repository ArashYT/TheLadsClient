package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;

public class PingViewModule extends Module {
    public PingViewModule() {
        super("PingView", "Show numerical ping in player tab list.");
        addOption(new BoolOption("Show Numbers", true));
        addOption(new DropdownOption("Color Mode", 0, "Latency", "Static"));
        addOption(new BoolOption("Hide Ping", false));
        addOption(new BoolOption("Text Shadow", true));
        addOption(new BoolOption("Small Numbers", false));
        addOption(new BoolOption("Hide False Ping", false));
        addOption(new ColorOption("Static Color", false, 0xffffffff));
        // TabTweaks 1.5.11 latency bands, adapted under LGPL-3.0-only.
        addOption(new ColorOption("Ping 0-74", false, -15466667));
        addOption(new ColorOption("Ping 75-144", false, -14773218));
        addOption(new ColorOption("Ping 145-199", false, -4733653));
        addOption(new ColorOption("Ping 200-299", false, -13779));
        addOption(new ColorOption("Ping 300-399", false, -6458098));
        addOption(new ColorOption("Ping 400+", false, -4318437));
    }
}
