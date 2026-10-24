package com.thelads.core.modules;

import com.thelads.core.client.DarkMode;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class DarkModeModule extends Module {
    public final BoolOption darkenContainers;
    public final SliderOption backgroundDim;

    public DarkModeModule() {
        super(DarkMode.MODULE_NAME, "Modern dark theme for vanilla GUI menus, inventories and container screens.");
        darkenContainers = addOption(new BoolOption("Darken Containers", true));
        backgroundDim = addOption(new SliderOption("Background Dim", 60, 0, 100, 5).percent());
        DarkMode.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        DarkMode.refresh();
    }
}
