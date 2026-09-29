package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;

public class KillBannerModule extends Module {
    public final ColorOption textColor = new ColorOption("Text Color", false, 0xFFFF0000); // Red by default
    public final DropdownOption bannerStyle = new DropdownOption("Style", 0, "Base", "Reaver");
    public final BoolOption sound = new BoolOption("Sound", true);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final SliderOption duration = new SliderOption("Duration", 2.5, 1, 5, .25);

    public KillBannerModule() {
        super("KillBanner", "Show a banner for player kills reported by the server's statistics.");
        addOption(bannerStyle);
        addOption(textColor);
        addOption(sound);
        addOption(volume);
        addOption(duration);
    }

}
