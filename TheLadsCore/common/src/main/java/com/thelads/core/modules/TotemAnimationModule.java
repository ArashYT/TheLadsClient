package com.thelads.core.modules;

import com.thelads.core.client.TotemAnimation;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class TotemAnimationModule extends Module {
    public final BoolOption hideAnimation;
    public final SliderOption totemScale;
    public final SliderOption totemOpacity;

    public TotemAnimationModule() {
        super(TotemAnimation.MODULE_NAME, "Customize or hide the Totem of Undying screen pop animation.");
        hideAnimation = addOption(new BoolOption("Hide Totem Animation", false));
        totemScale = addOption(new SliderOption("Totem Scale", 100, 10, 200, 5).percent());
        totemOpacity = addOption(new SliderOption("Totem Opacity", 100, 0, 100, 5).percent());
        TotemAnimation.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        TotemAnimation.refresh();
    }
}
