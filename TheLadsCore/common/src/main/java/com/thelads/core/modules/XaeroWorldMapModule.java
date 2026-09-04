package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public class XaeroWorldMapModule extends Module {
    public XaeroWorldMapModule() {
        super("XaeroWorldmap", "Fullscreen and minimap world overview.");
        addOption(new BoolOption("Minimap", true));
        addOption(new BoolOption("Cave Mode", false));
    }
}
