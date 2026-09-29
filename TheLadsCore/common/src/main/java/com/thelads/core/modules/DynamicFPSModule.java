package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class DynamicFPSModule extends Module {
    public DynamicFPSModule() {
        super("DynamicFPS", "Background, idle and battery profiles with sound fades, graphics and VSync controls. 0 FPS suspends rendering; 260 is unlimited.");
        addOption(new SliderOption("Unfocused FPS", 60, 0, 260, 1));
        addOption(new SliderOption("Hidden FPS", 30, 0, 260, 1));
        addOption(new DropdownOption("Mode", 1, "Aggressive", "Balanced", "Off"));
        addOption(new ActionOption("Background Profiles", "Edit all profiles"));
    }
}
