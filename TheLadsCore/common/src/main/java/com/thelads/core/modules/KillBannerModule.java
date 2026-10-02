package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.BoolOption;

public class KillBannerModule extends Module {
    public final DropdownOption bannerStyle = new DropdownOption("Style", 1, "Base", "Reaver", "Rogue");
    public final DropdownOption reaverVariant = new DropdownOption("Reaver Variant", 0, "Base", "Red", "Black", "White");
    public final DropdownOption rogueVariant = new DropdownOption("Rogue Variant", 0, "Base", "Green", "Red", "Blue");
    public final BoolOption headshotText = new BoolOption("Headshot Text", true);
    public final ColorOption textColor = new ColorOption("Text Color", false, 0xFFFF0000); // the Base style's label
    public final BoolOption sound = new BoolOption("Sound", true);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final SliderOption duration = new SliderOption("Duration", 2, 1, 5, .25);
    public final SliderOption size = new SliderOption("Size", 100, 50, 150, 10);

    public KillBannerModule() {
        super("KillBanner", "Valorant Reaver and Rogue kill banners, with their animations and sounds, for player kills the server reports.");
        addOption(bannerStyle);
        addOption(reaverVariant);
        addOption(rogueVariant);
        addOption(headshotText);
        addOption(textColor);
        addOption(sound);
        addOption(volume);
        addOption(duration);
        addOption(size);
    }

}
