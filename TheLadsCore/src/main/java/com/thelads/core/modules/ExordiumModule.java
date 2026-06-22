package com.thelads.core.modules;

import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

public class ExordiumModule extends Module {
    public ExordiumModule() {
        super("Exordium", "Limits GUI framerate to save resources. Built-in native clone.");
        addOption(new DoubleOption("GUI FPS Limit", 30.0, 5.0, 144.0));
    }

    public static int getFpsLimit() {
        Module m = ModuleManager.getInstance().getModule("Exordium");
        if (m == null || !m.isEnabled()) return -1;
        Option o = m.getOption("GUI FPS Limit");
        if (o instanceof DoubleOption) {
            return (int) ((DoubleOption) o).get();
        }
        return 30;
    }
}
