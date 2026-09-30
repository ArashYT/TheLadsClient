package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class ToggleNametagsModule extends Module {
    public ToggleNametagsModule() {
        super("Nametags", "Control visibility and background of player nametags.");
        addOption(new BoolOption("Show Own Nametag in Third Person", false));
        addOption(new BoolOption("Render Background", true));
    }
}
