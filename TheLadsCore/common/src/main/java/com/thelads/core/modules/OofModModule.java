package com.thelads.core.modules;

import com.thelads.core.client.sound.OofMod;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class OofModModule extends Module {
    public final DropdownOption hurtSound;
    public final DropdownOption deathSound;
    public final SliderOption volume;
    public final BoolOption localOnly;

    public OofModModule() {
        super(OofMod.MODULE_NAME, "Replace player hurt and death grunts with custom audio effects like Roblox 'oof'.");
        hurtSound = addOption(new DropdownOption("Hurt Sound", 0, "Oof", "Bell", "Bonk", "Pop", "None"));
        deathSound = addOption(new DropdownOption("Death Sound", 0, "Oof", "Bell", "Bonk", "Pop", "None"));
        volume = addOption(new SliderOption("Sound Volume", 100, 0, 100, 5).percent());
        localOnly = addOption(new BoolOption("Local Player Only", true));
        OofMod.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        OofMod.refresh();
    }
}
