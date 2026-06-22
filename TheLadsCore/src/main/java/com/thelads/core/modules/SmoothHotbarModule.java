package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;

public class SmoothHotbarModule extends Module {
    public SmoothHotbarModule() {
        super("SmoothHotbar", "Animates the hotbar selection box smoothly.");
        addOption(new DropdownOption("Speed", 1, "Slow", "Normal", "Fast"));
    }
}
