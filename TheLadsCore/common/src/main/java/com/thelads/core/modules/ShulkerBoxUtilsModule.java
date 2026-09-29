package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;

public final class ShulkerBoxUtilsModule extends Module {
    public ShulkerBoxUtilsModule() {
        super("ShulkerBoxUtils", "Shulker contents, inventory badges and world icons from actual item data or last-observed container contents.");
        addOption(new BoolOption("World Icon", true));
        addOption(new BoolOption("Inventory Badge", true));
        addOption(new BoolOption("Free Slot Bar", true));
        addOption(new BoolOption("Contents Preview", true));
        addOption(new BoolOption("Item Counts", true));
        addOption(new DropdownOption("Display Mode", 0, "First Item", "Single Item Type"));
        addOption(new SliderOption("Distance", 24, 8, 64, 4));
        addOption(new SliderOption("Icon Size", 65, 25, 125, 5));
        addOption(new SliderOption("Height", 35, 10, 100, 5));
        addOption(new BoolOption("Animate", true));
        addOption(new BoolOption("Remember Observed Contents", true));
        addOption(new BoolOption("Persist Observed Contents", true));
        setEnabled(true);
    }
}
