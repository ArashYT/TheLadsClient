package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;

public class SmoothHotbarModule extends Module {
    public SmoothHotbarModule() {
        super("SmoothHotbar", "Animates the hotbar selection box smoothly.");
        addOption(new CycleOption("Speed", 1, "Slow", "Normal", "Fast"));
    }
}
