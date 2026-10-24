package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Toast Control (Feather): recipe, tutorial and advancement pop-ups (on 1.8.9 the achievement pop-ups and the "Open your inventory"
 * hint) hidden or moved. Visual only. The adapters read what {@link #refresh} cached.
 */
public final class ToastControlModule extends Module {
    public static final String NAME = "ToastControl";
    public static final int RECIPE = 0, TUTORIAL = 1, ADVANCEMENT = 2, OTHER = 3;

    public final BoolOption recipes = addOption(new BoolOption("Hide Recipe Toasts", true));
    public final BoolOption tutorials = addOption(new BoolOption("Hide Tutorial Toasts", true));
    public final BoolOption advancements = addOption(new BoolOption("Hide Advancement Toasts", false));
    public final BoolOption others = addOption(new BoolOption("Hide Other Toasts", false));
    public final SliderOption x = addOption(new SliderOption("X Offset", 0, -500, 500, 1));
    public final SliderOption y = addOption(new SliderOption("Y Offset", 0, -500, 500, 1));

    /** Cached each tick: which kinds are hidden (bit per kind; 0 while off) and the offset in GUI pixels. */
    public int hiddenKinds;
    public int dx, dy;

    public ToastControlModule() {
        super(NAME, "Hide or move the recipe, tutorial and advancement pop-ups.");
    }

    public void refresh() {
        boolean on = isEnabled();
        hiddenKinds = !on ? 0 : (recipes.get() ? 1 << RECIPE : 0) | (tutorials.get() ? 1 << TUTORIAL : 0)
            | (advancements.get() ? 1 << ADVANCEMENT : 0) | (others.get() ? 1 << OTHER : 0);
        dx = on ? x.getIntValue() : 0;
        dy = on ? y.getIntValue() : 0;
    }

    public boolean hides(int kind) {
        return (hiddenKinds & 1 << kind) != 0;
    }
}
