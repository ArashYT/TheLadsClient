package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class DynamicFPSModule extends Module {
    public DynamicFPSModule() {
        super("DynamicFPS", "Reduce framerate when the game is unfocused or idle.");
        addOption(new SliderOption("Unfocused FPS", 15, 1, 60, 1));
        addOption(new SliderOption("Hidden FPS", 5, 1, 30, 1));
        addOption(new DropdownOption("Mode", 0, "Aggressive", "Balanced", "Off"));
    }
}
