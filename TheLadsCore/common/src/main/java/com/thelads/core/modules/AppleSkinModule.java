package com.thelads.core.modules;

import com.thelads.core.config.Module;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.SliderOption;

public class AppleSkinModule extends Module {
    public AppleSkinModule() {
        super("AppleSkin", "Hunger, saturation and health a held food restores, saturation and exhaustion on the hunger bar, food values in tooltips.");
        addOption(new BoolOption("Show Saturation", true));
        addOption(new BoolOption("Show Food Values", true));
        addOption(new BoolOption("Show Exhaustion", true));
        addOption(new BoolOption("Show Saturation Overlay", true));
        addOption(new BoolOption("Show Health Overlay", true));
        addOption(new BoolOption("Food Tooltips", true));
        addOption(new BoolOption("Tooltips Always Visible", true));
        addOption(new BoolOption("Offhand Food", true));
        addOption(new BoolOption("Vanilla Animations", true));
        addOption(new SliderOption("Overlay Opacity", 65, 0, 100, 5).percent());
    }
}
