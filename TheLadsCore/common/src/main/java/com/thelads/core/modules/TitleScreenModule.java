package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class TitleScreenModule extends Module {
    public TitleScreenModule() {
        super("TitleScreen", "Customize the title screen elements.");
        addOption(new SliderOption("Account Card Scale", 100, 50, 150, 5));
    }
}
