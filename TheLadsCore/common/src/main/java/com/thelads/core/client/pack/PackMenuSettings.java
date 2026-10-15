package com.thelads.core.client.pack;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;

/** Settings access for Resource pack menu customizations. */
public final class PackMenuSettings {
    public static final String MODULE_NAME = "ResourcePacks";
    public static final String OPTION_TRANSPARENT = "Transparent pack menu";
    public static final String OPTION_IGNORE_INCOMPATIBLE = "Ignore incompatible packs";
    public static final String OPTION_NO_COMPAT_CHECK = "No pack compat check";

    private PackMenuSettings() {}

    public static boolean isTransparent() {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        if (m == null) return true;
        Option o = m.getOption(OPTION_TRANSPARENT);
        return (o instanceof BoolOption b) ? b.get() : true;
    }

    /** Packs made for another version are left out of the Available list (never the selected one). Off by default. */
    public static boolean ignoreIncompatible() { return bool(OPTION_IGNORE_INCOMPATIBLE, false); }

    /** Packs made for another version load with no red label and no confirm popup. On by default. */
    public static boolean noCompatCheck() { return bool(OPTION_NO_COMPAT_CHECK, true); }

    private static boolean bool(String option, boolean fallback) {
        Module m = ModuleManager.getInstance().getModule(MODULE_NAME);
        Option o = m == null ? null : m.getOption(option);
        return o instanceof BoolOption b ? b.get() : fallback;
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
