package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Color Saturation (Lunar, Badlion, Feather, OneClient): grades the world's colors with Lunar's sliders, 0-10 with 5 unchanged
 * (Hue 0 = no turn), plus Vibrance (OneClient, Feather) and Grayscale. The HUD and menus keep their colors. Visual only.
 * Shared math: client.ColorGrade; each version draws it after the world (skipped while a shader pack renders the world).
 */
public final class ColorSaturationModule extends Module {
    public static final String NAME = "ColorSaturation";

    public final SliderOption hue = addOption(new SliderOption("Hue", 0, 0, 10, 0.1));
    public final SliderOption saturation = addOption(new SliderOption("Saturation", 5, 0, 10, 0.1));
    public final SliderOption vibrance = addOption(new SliderOption("Vibrance", 5, 0, 10, 0.1));
    public final SliderOption brightness = addOption(new SliderOption("Brightness", 5, 0, 10, 0.1));
    public final SliderOption contrast = addOption(new SliderOption("Contrast", 5, 0, 10, 0.1));
    public final BoolOption grayscale = addOption(new BoolOption("Grayscale", false));

    public ColorSaturationModule() {
        super(NAME, "World color grading: hue, saturation, vibrance, brightness, contrast or grayscale. 5 leaves a slider unchanged.");
    }
}
