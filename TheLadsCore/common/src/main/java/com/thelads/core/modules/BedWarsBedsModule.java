package com.thelads.core.modules;

import com.thelads.core.client.BedWarsBeds;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

public class BedWarsBedsModule extends Module {
    public final DropdownOption colorMode;
    public final BoolOption destructionEffect;

    public BedWarsBedsModule() {
        super(BedWarsBeds.MODULE_NAME, "Render colored beds and bed destruction effects in Minecraft 1.8.9 Bed Wars.");
        colorMode = addOption(new DropdownOption("Bed Color Mode", 0, "Team Color", "Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray"));
        destructionEffect = addOption(new BoolOption("Destruction Effect", true));
        BedWarsBeds.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        BedWarsBeds.refresh();
    }
}
