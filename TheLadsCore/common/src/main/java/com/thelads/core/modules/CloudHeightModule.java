package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.OptionalDouble;

/**
 * Cloud Height: control the vertical Y altitude where clouds render.
 * The slider's last position is labelled "MAX", which dynamically maps to 256 on 1.8.9,
 * and the world's top build height on newer versions (e.g. 320 in the Overworld, or datapack heights).
 */
public class CloudHeightModule extends Module {
    public static final String NAME = "Cloud Height";
    public static final String HEIGHT = "Height";
    public static final double MIN_Y = 64;
    public static final double MAX_Y = 256;
    public static final double MAX_VALUE = MAX_Y;
    public static final double STEP = 4;

    public final SliderOption height;

    public CloudHeightModule() {
        super(NAME, "Adjust the height at which clouds render in the world.");
        this.height = new SliderOption(HEIGHT, MAX_Y, MIN_Y, MAX_Y, STEP) {
            @Override
            public String display() {
                if (getValue() >= getMax()) {
                    return "MAX";
                }
                return super.display();
            }

            @Override
            public OptionalDouble parse(String text) {
                if (text != null && text.trim().equalsIgnoreCase("MAX")) {
                    return OptionalDouble.of(getMax());
                }
                return super.parse(text);
            }
        }.editable();
        addOption(height);
        setEnabled(true);
    }

    public SliderOption getHeightOption() {
        return height;
    }

    public double getHeightSetting() {
        return height.getValue();
    }

    /**
     * Resolves the slider value to an actual world Y coordinate.
     * When at or above MAX_Y (256), resolves to the world's top build height (e.g. 256 on 1.8.9, 320 on modern overworld).
     * Otherwise, returns the explicit slider value.
     */
    public static float resolveHeight(double sliderValue, int worldMaxHeight) {
        if (sliderValue >= MAX_Y) {
            return (float) worldMaxHeight;
        }
        return (float) sliderValue;
    }

    /**
     * Helper called by cloud rendering hooks on all versions.
     * Returns the target cloud height, or vanillaHeight if the module is disabled or vanilla height is NaN.
     */
    public static float getTargetCloudHeight(float vanillaHeight, int worldMaxHeight) {
        if (Float.isNaN(vanillaHeight)) {
            return Float.NaN;
        }
        com.thelads.core.config.ModuleManager mm = com.thelads.core.config.ModuleManager.getInstance();
        if (mm == null) {
            return vanillaHeight;
        }
        Module m = mm.getModule(NAME);
        if (m == null || !m.isEnabled()) {
            return vanillaHeight;
        }
        Option opt = m.getOption(HEIGHT);
        if (opt instanceof SliderOption slider) {
            return resolveHeight(slider.getValue(), worldMaxHeight);
        }
        return vanillaHeight;
    }
}
