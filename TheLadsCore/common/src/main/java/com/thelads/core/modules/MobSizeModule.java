package com.thelads.core.modules;

import com.thelads.core.client.MobSize;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class MobSizeModule extends Module {
    public final SliderOption mobScale;
    public final BoolOption scalePlayers;

    public MobSizeModule() {
        super(MobSize.MODULE_NAME, "Adjust client-side rendering scale of mobs and entities.");
        mobScale = addOption(new SliderOption("Mob Scale", 1.0, 0.25, 2.0, 0.05));
        scalePlayers = addOption(new BoolOption("Scale Players", false));
        MobSize.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        MobSize.refresh();
    }
}
