package com.thelads.core.modules;

import com.google.gson.JsonParser;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FullbrightModuleTest {
    private static SliderOption brightness(FullbrightModule module) { return (SliderOption) module.getOption("Brightness"); }

    @Test void oneBrightnessPercentageInStepsOfFive() {
        var module = new FullbrightModule();
        assertEquals(List.of("Brightness"), module.getOptions().stream().map(Option::getName).toList());
        var slider = brightness(module);
        assertEquals(100, slider.getValue(), "full bright by default");
        assertEquals("100%", slider.display());
        assertEquals(5, slider.getStep());
        assertEquals(0, slider.getMin());
        assertEquals(100, slider.getMax());
    }

    @Test void thePercentageIsHowFarTheLightmapIsLifted() {
        var module = new FullbrightModule();
        module.setEnabled(false);
        assertEquals(0, module.lightmapFactor(), "off: nothing");
        module.setEnabled(true);
        for (int percent : new int[] {0, 5, 50, 100}) {
            brightness(module).setValue(percent);
            assertEquals(percent / 100f, module.lightmapFactor(), 1e-6f, percent + "% lifts the lightmap " + percent + "%");
        }
        assertEquals(1, module.effectiveGamma(), "the gamma is Minecraft's brightest setting at any percentage");
    }

    @Test void whatIsSavedLoadsBackTheSame() {
        var module = new FullbrightModule();
        brightness(module).setValue(35);
        var again = new FullbrightModule();
        brightness(again).load(brightness(module).save());
        assertEquals(35, brightness(again).getValue());
        brightness(again).load(JsonParser.parseString("42.9"));
        assertEquals(45, brightness(again).getValue(), "rounded to a step");
        brightness(again).reset();
        assertEquals(100, brightness(again).getValue(), "reset goes back to the default");
    }
}
