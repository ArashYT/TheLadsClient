package com.thelads.core.client.pack;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

/** Settings access for Resource pack menu customizations. */
public final class PackMenuSettings {
    public static final String MODULE_NAME = "ResourcePacks";
    public static final String OPTION_TRANSPARENT = "Transparent pack menu";

    private PackMenuSettings() {}

    public static boolean isTransparent() {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        if (m == null) return true;
        Option o = m.getOption(OPTION_TRANSPARENT);
        return (o instanceof BoolOption b) ? b.get() : true;
    }

    public static void setTransparent(boolean transparent) {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        if (m != null) {
            Option o = m.getOption(OPTION_TRANSPARENT);
            if (o instanceof BoolOption b) {
                b.set(transparent);
            }
        }
    }
}
