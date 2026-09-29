package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.TextOption;

public class EnhancedToolbarsModule extends Module {
    public EnhancedToolbarsModule() {
        super("EnhancedToolbars", "Durability bars, numbers or condition text, with colors and item filters.");
        addOption(new BoolOption("Detailed Durability", true));
        addOption(new BoolOption("Show Max Durability", true));
        addOption(new BoolOption("Colorize Durability", true));
        addOption(new BoolOption("Show Item Attributes", true));
        addOption(new DropdownOption("Durability Style", 0, "Numbers", "Bar", "Text"));
        addOption(new BoolOption("Show Durability Hint", true));
        addOption(new DropdownOption("Durability Color Style", 0, "Varying", "Base", "Gold"));
        addOption(new ColorOption("Durability Base Color", false, 0xffaaaaaa));
        addOption(new BoolOption("Only Vanilla Tools", false));
        addOption(new BoolOption("Show When Full", true));
        addOption(new TextOption("Excluded Mods", "tconstruct,supplementaries"));
    }
}
