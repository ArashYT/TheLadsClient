package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class PerformanceManagerModule extends Module {
    public PerformanceManagerModule() {
        super("Performance", "Tweak memory allocation, GC pacing, and rendering hints.");
        addOption(new BoolOption("Aggressive Memory Cleaner", false));
        addOption(new BoolOption("Reduce Background FPS", true));
    }
}
