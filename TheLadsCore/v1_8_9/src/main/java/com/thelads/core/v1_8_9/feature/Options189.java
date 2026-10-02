package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.StringOption;

/** Module and option reads for the 1.8.9 features, as the other versions' NativeQualityOfLife helpers. */
public final class Options189 {
    private Options189() {}

    public static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }

    private static Option option(String module, String option) {
        Module value = module(module);
        return value != null ? value.getOption(option) : null;
    }

    public static boolean bool(String module, String option, boolean fallback) {
        Option value = option(module, option);
        return value instanceof BoolOption ? ((BoolOption) value).get() : fallback;
    }

    public static int choice(String module, String option, int fallback) {
        Option value = option(module, option);
        return value instanceof DropdownOption ? ((DropdownOption) value).getIndex() : fallback;
    }

    public static double number(String module, String option, double fallback) {
        Option value = option(module, option);
        return value instanceof SliderOption ? ((SliderOption) value).getValue() : fallback;
    }

    public static String string(String module, String option, String fallback) {
        Option value = option(module, option);
        return value instanceof StringOption ? ((StringOption) value).get() : fallback;
    }

    /** A colour option's ARGB, the HUD's global colour when it follows it. */
    public static int color(String module, String option, int fallback) {
        Option value = option(module, option);
        if (!(value instanceof ColorOption)) return fallback;
        ColorOption color = (ColorOption) value;
        return color.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : color.getColor();
    }
}
