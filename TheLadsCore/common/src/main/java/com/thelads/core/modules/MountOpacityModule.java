package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/** Mount Opacity (OneClient): the horse, pig or other mob you ride is drawn see-through, so it hides less of the view. */
public final class MountOpacityModule extends Module {
    public static final String NAME = "Mount Opacity";

    public final SliderOption opacity = addOption(new SliderOption("Opacity", 50, 0, 100, 5).percent());

    /** Cached each tick: the module is on and the mount is see-through, and how opaque (0 to 1). */
    public boolean see;
    public float alpha = 1;

    public MountOpacityModule() {
        super(NAME, "The mob you ride is drawn see-through.");
    }

    public void refresh() {
        alpha = (float) opacity.getValue() / 100;
        see = isEnabled() && alpha < 1;
    }
}
