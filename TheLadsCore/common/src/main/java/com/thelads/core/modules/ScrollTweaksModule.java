package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

public final class ScrollTweaksModule extends Module {
    public static final String NAME = "ScrollTweaks";

    public final BoolOption invertScroll;
    public final BoolOption noWrap;

    public ScrollTweaksModule() {
        super(NAME, "Custom hotbar mouse-wheel scrolling options: invert scroll direction and prevent slot wrap-around.");
        invertScroll = addOption(new BoolOption("Invert Direction", false));
        noWrap = addOption(new BoolOption("Disable Wrap-around", false));
        setEnabled(false);
    }

    public boolean isScrollInverted() {
        return isEnabled() && invertScroll.get();
    }

    public boolean isNoWrap() {
        return isEnabled() && noWrap.get();
    }
}
