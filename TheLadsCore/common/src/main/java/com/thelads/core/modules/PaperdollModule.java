package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class PaperdollModule extends Module {
    public PaperdollModule() {
        super("Paperdoll", "Renders your player character on the screen.");
        addOption(new BoolOption("Show in First Person", false));
        addOption(new BoolOption("Show in Third Person", true));
        addOption(new BoolOption("Always Display", true));
        addOption(new SliderOption("Display Time (ticks)", 40, 0, 200, 5));
        // Existing indices are retained; pitch-only is appended for upstream parity.
        addOption(new DropdownOption("Head Movement", 0, "Yaw and Pitch", "Yaw Only", "None", "Pitch Only"));
        addOption(new SliderOption("Model Scale", 4, 1, 24, 1));
        addOption(new DropdownOption("Anchor", 0, "HUD Position", "Top Left", "Top Center", "Top Right",
                "Center Left", "Center", "Center Right", "Bottom Left", "Bottom Center", "Bottom Right"));
        addOption(new SliderOption("X Offset", 0, -500, 500, 1));
        addOption(new SliderOption("Y Offset", 0, -500, 500, 1));
        addOption(new SliderOption("Default Rotation", 15, 0, 45, 1));
        addOption(new SliderOption("Maximum Yaw", 30, 0, 90, 1));
        addOption(new SliderOption("Maximum Pitch", 30, 0, 90, 1));
        addOption(new SliderOption("Model Opacity", 100, 5, 100, 5));
        addOption(new BoolOption("Sprinting", true));
        addOption(new BoolOption("Swimming", true));
        addOption(new BoolOption("Crawling", true));
        addOption(new BoolOption("Crouching", true));
        addOption(new BoolOption("Creative Flying", true));
        addOption(new BoolOption("Elytra Gliding", true));
        addOption(new BoolOption("Riding", false));
        addOption(new BoolOption("Spin Attacking", false));
        addOption(new BoolOption("Using Items", false));
        setCategory(Category.HUD);
    }
}
