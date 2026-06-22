package com.thelads.core.modules;

import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;

public class KillBannerModule extends Module {
    public final ColorOption textColor = new ColorOption("Text Color", false, 0xFFFF0000); // Red by default
    public final DropdownOption bannerStyle = new DropdownOption("Style", 0, "Base", "Reaver");

    public KillBannerModule() {
        super("KillBanner", "Show a Valorant-style kill banner on player kills.");
        addOption(bannerStyle);
        addOption(textColor);
    }

    public void handleTestKillBannerCommand(int level) {
        // Simulate a kill event to test the kill banner
        com.thelads.core.modules.killbanner.KillBannerRenderer.triggerKill(level);
    }
}
