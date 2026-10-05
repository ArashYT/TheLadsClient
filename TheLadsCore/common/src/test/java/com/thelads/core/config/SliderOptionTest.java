package com.thelads.core.config;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The slider's step, the display without raw decimals and the typed value of an editable slider. */
class SliderOptionTest {
    private static SliderOption percent() { return new SliderOption("Opacity", 65, 0, 100, 5).percent(); }

    @Test void everyWayOfSettingTheValueSnapsToTheStep() {
        var slider = percent();
        for (double raw : new double[] {67.43, 66, 63, 62.6, 0.4, 99.9}) {
            slider.setValue(raw);
            assertEquals(0, slider.getValue() % 5, 1e-9, raw + " snapped to " + slider.getValue());
            assertTrue(Math.abs(slider.getValue() - raw) <= 2.5 + 1e-9, raw + " goes to the nearest step, not " + slider.getValue());
        }
        slider.setValue(67.43); assertEquals(65, slider.getValue());
        slider.setValue(68); assertEquals(70, slider.getValue());
        slider.setValue(-12); assertEquals(0, slider.getValue(), "below the range: the minimum");
        slider.setValue(1000); assertEquals(100, slider.getValue(), "above the range: the maximum");
        slider.setValue(Double.NaN); assertEquals(0, slider.getValue());
    }

    @Test void savedValuesRoundToTheNearestStepWhenLoaded() {
        var slider = percent();
        slider.load(new JsonPrimitive(67.43)); assertEquals(65, slider.getValue());
        slider.load(new JsonPrimitive(68.0)); assertEquals(70, slider.getValue());
        slider.load(new JsonPrimitive("not a number")); assertEquals(70, slider.getValue(), "a broken value changes nothing");
        assertEquals(70.0, slider.save().getAsDouble(), "what is saved is the stepped value, under the same option name");
        assertEquals("Opacity", slider.getName());
    }

    @Test void theValueIsShownWithoutRawDecimals() {
        var slider = percent();
        assertEquals("65%", slider.display());
        slider.setValue(67.43); assertEquals("65%", slider.display());
        assertEquals("10", SliderOption.format(10.0));
        assertEquals("1.5", SliderOption.format(1.5));
        assertEquals("0", SliderOption.format(0));
        assertEquals("100", SliderOption.format(100));
        assertEquals("1.5", new SliderOption("Multiplier", 1.5, 1, 10, .5).display(), "a plain slider has no suffix");
    }

    @Test void aTypedWholeNumberInRangeIsAcceptedAndRoundedToTheStep() {
        var slider = percent();
        assertEquals(70, slider.parse("70").getAsDouble());
        assertEquals(65, slider.parse("67").getAsDouble(), "typing allows any whole number; it lands on the nearest step like the slider does");
        assertEquals(70, slider.parse(" 68 ").getAsDouble());
        assertEquals(70, slider.parse("70%").getAsDouble(), "the suffix may be typed");
        assertEquals(0, slider.parse("0").getAsDouble());
        assertEquals(100, slider.parse("100").getAsDouble());
        assertEquals(65, slider.parse("065").getAsDouble());
    }

    @Test void anythingElseIsNotAnAnswer() {
        var slider = percent();
        for (String text : new String[] {null, "", "  ", "abc", "7.5", "70,5", "1e2", "101", "-5", "+5", "6 5", "%", "70 %%", "99999999999"})
            assertTrue(slider.parse(text).isEmpty(), "'" + text + "' reverts");
        var signed = new SliderOption("Rotation", 0, -180, 180, 1).editable();
        assertEquals(-90, signed.parse("-90").getAsDouble(), "a range below zero takes a minus sign");
        assertTrue(signed.parse("181").isEmpty());
        assertTrue(new SliderOption("Plain", 1, 0, 10, 1).parse("5%").isEmpty(), "no suffix is accepted where none is shown");
        assertEquals(65, slider.getValue(), "parsing never changes the option");
    }

    @Test void editingIsOptIn() {
        assertFalse(new SliderOption("Plain", 1, 0, 10, 1).isEditable());
        assertTrue(new SliderOption("Plain", 1, 0, 10, 1).editable().isEditable());
        assertTrue(percent().isEditable());
    }
}
