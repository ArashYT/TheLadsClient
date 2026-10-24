package com.thelads.core.client.pack;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.OldAnimationsModule;

/** Settings access for Resource pack menu customizations. */
public final class PackMenuSettings {
    public static final String MODULE_NAME = "ResourcePacks";
    public static final String OPTION_TRANSPARENT = "Transparent pack menu";
    public static final String OPTION_IGNORE_INCOMPATIBLE = "Ignore incompatible packs";
    public static final String OPTION_NO_COMPAT_CHECK = "No pack compat check";
    public static final String OPTION_PROFILES = "Pack profiles";

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

    /** The pack screen shows the Profiles button (PackProfiles). On by default. */
    public static boolean profiles() { return bool(OPTION_PROFILES, true); }

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
    /**
     * The pack screen's Low Fire / Low Shield buttons: the 1.7 Animations options, there because a pack that already lowers the fire
     * overlay or shields stacks with them and pushes both off the screen.
     */
    public static String overlayLabel(OldAnimationsModule.Feature feature) {
        return feature.option + ": " + (overlayOn(feature) ? "ON" : "OFF");
    }

    /** Lads lowers it now: 1.7 Animations is on and so is the option. */
    public static boolean overlayOn(OldAnimationsModule.Feature feature) {
        return overlayAvailable() && oldAnimations().option(feature).get();
    }

    /** With 1.7 Animations off nothing is lowered and the buttons are inactive. */
    public static boolean overlayAvailable() {
        OldAnimationsModule m = oldAnimations();
        return m != null && m.isEnabled();
    }

    /** Flips the option and saves (off the render thread). */
    public static void toggleOverlay(OldAnimationsModule.Feature feature) {
        if (!overlayAvailable()) return;
        oldAnimations().option(feature).toggle();
        ConfigManager.saveLater();
    }

    private static OldAnimationsModule oldAnimations() {
        Module m = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        return m instanceof OldAnimationsModule o ? o : null;
    }
}
