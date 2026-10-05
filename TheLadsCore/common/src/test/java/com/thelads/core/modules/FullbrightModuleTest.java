package com.thelads.core.modules;

import com.google.gson.JsonParser;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FullbrightModuleTest {
    private static SliderOption gamma(FullbrightModule module) { return (SliderOption) module.getOption("Gamma"); }

    @Test void theGammaSliderIsAPercentageInStepsOfFiveAndKeepsItsOptionNames() {
        var module = new FullbrightModule();
        assertEquals(List.of("Gamma", "Brightness Multiplier"), module.getOptions().stream().map(Option::getName).toList());
        var slider = gamma(module);
        assertEquals(65, slider.getValue());
        assertEquals("65%", slider.display());
        assertTrue(slider.isEditable());
        assertEquals(5, slider.getStep());
        assertEquals(0, slider.getMin());
        assertEquals(100, slider.getMax());
    }

    @Test void thePercentageMapsOntoGammaOneToFifteen() {
        var module = new FullbrightModule();
        gamma(module).setValue(0); assertEquals(1, module.getGamma(), 1e-9, "0% is Minecraft's brightest setting");
        gamma(module).setValue(100); assertEquals(15, module.getGamma(), 1e-9);
        gamma(module).setValue(65); assertEquals(10.1, module.getGamma(), 1e-9, "the default is 10, as before");
        ((SliderOption) module.getOption("Brightness Multiplier")).setValue(2);
        assertEquals(20.2, module.effectiveGamma(), 1e-9, "the multiplier still multiplies");
    }

    @Test void gammaSavedBeforeThisVersionCarriesOver() {
        for (Object[] row : new Object[][] {{"{\"value\":10.0}", 65.0}, {"{\"value\":15.0}", 100.0}, {"{\"value\":1.0}", 0.0},
                {"{\"value\":3.5}", 20.0}, {"{\"value\":7.0}", 45.0}, {"{\"value\":99}", 100.0}}) {
            var module = new FullbrightModule();
            gamma(module).load(JsonParser.parseString((String) row[0]));
            assertEquals((double) row[1], gamma(module).getValue(), row[0].toString());
        }
        var module = new FullbrightModule();
        gamma(module).load(JsonParser.parseString("{\"nothing\":1}")); assertEquals(65, gamma(module).getValue(), "no value: the default");
        gamma(module).load(JsonParser.parseString("{\"value\":\"x\"}")); assertEquals(65, gamma(module).getValue());
        gamma(module).load(JsonParser.parseString("42.9")); assertEquals(45, gamma(module).getValue(), "the new format is a bare number, rounded to a step");
    }

    @Test void whatIsSavedLoadsBackTheSame() {
        var module = new FullbrightModule();
        gamma(module).setValue(35);
        var saved = gamma(module).save();
        var again = new FullbrightModule();
        gamma(again).load(saved);
        assertEquals(35, gamma(again).getValue());
        gamma(again).reset();
        assertEquals(65, gamma(again).getValue(), "reset goes back to the default");
    }
}
