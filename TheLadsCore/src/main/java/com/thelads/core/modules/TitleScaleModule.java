package com.thelads.core.modules;

import com.thelads.core.config.DoubleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

public class TitleScaleModule extends Module {
    public TitleScaleModule() {
        super("Title Scale", "Change the size of the title screen text and elements.");
        addOption(new DoubleOption("Scale", 1.0, 0.1, 5.0));
    }

    public static float getScale() {
        Module m = ModuleManager.getInstance().getModule("Title Scale");
        if (m == null || !m.isEnabled()) return 1.0f;
        Option o = m.getOption("Scale");
        if (o instanceof DoubleOption) {
            return (float) ((DoubleOption) o).get();
        }
        return 1.0f;
    }
}
