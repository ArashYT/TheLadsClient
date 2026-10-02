package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/** Live Lads module settings, read where they are used (26.x: NativeQualityOfLife's helpers). */
public final class Options189 {
    private Options189() {}

    public static Module module(String name) { return ModuleManager.getInstance().getModule(name); }

    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }

    public static boolean bool(String module, String option, boolean fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof BoolOption ? ((BoolOption) value.getOption(option)).get() : fallback;
    }

    public static int choice(String module, String option, int fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof DropdownOption ? ((DropdownOption) value.getOption(option)).getIndex() : fallback;
    }

    public static double number(String module, String option, double fallback) {
        Module value = module(module);
        return value != null && value.getOption(option) instanceof SliderOption ? ((SliderOption) value.getOption(option)).getValue() : fallback;
    }
}
