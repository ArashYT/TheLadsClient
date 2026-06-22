package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class PaperdollModule extends Module {
    public PaperdollModule() {
        super("Paperdoll", "Renders your player character on the screen.");
        addOption(new BoolOption("Show in First Person", false));
        addOption(new BoolOption("Always Display", true));
        addOption(new SliderOption("Display Time (ticks)", 40, 20, 200, 10));
        addOption(new DropdownOption("Head Movement", 0, "Yaw and Pitch", "Yaw Only", "None"));
    }
}
