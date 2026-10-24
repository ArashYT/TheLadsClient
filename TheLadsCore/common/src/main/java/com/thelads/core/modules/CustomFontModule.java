package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Custom fonts: allows replacing default Minecraft pixel font with smooth TrueType fonts and adjusting text scaling/shadow (parity with Badlion).
 */
public class CustomFontModule extends Module {
    public static final String NAME = "Custom Font";

    public final DropdownOption fontFamily;
    public final BoolOption smoothFont;
    public final BoolOption textShadow;
    public final SliderOption fontScale;

    public CustomFontModule() {
        super(NAME, "Replaces default Minecraft bitmap font with smooth scalable fonts.");
        fontFamily = addOption(new DropdownOption("Font Family", 0, "Default", "Sans-Serif", "Serif", "Monospaced"));
        smoothFont = addOption(new BoolOption("Smooth font", true));
        textShadow = addOption(new BoolOption("Text shadow", true));
        fontScale = addOption(new SliderOption("Font scale", 100, 50, 150, 5).percent());
        setEnabled(false);
    }
}
