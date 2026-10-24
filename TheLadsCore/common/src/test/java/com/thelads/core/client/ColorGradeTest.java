package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.modules.ColorSaturationModule;
import org.junit.jupiter.api.Test;

class ColorGradeTest {
    private final float[] m = new float[12];

    private float[] apply(float r, float g, float b) {
        float[] out = new float[3];
        for (int row = 0; row < 3; row++) out[row] = m[row * 4] * r + m[row * 4 + 1] * g + m[row * 4 + 2] * b + m[row * 4 + 3];
        return out;
    }

    @Test
    void defaultsChangeNothing() {
        assertFalse(ColorGrade.matrix(0, 5, 5, 5, false, m));
        assertFalse(ColorGrade.matrix(10, 5, 5, 5, false, m), "a full hue turn is no turn");
        ColorGrade grade = new ColorGrade();
        ColorSaturationModule module = new ColorSaturationModule();
        assertTrue(grade.update(module));
        assertFalse(grade.active);
        assertFalse(grade.update(module), "nothing moved: nothing to upload");
    }

    @Test
    void grayscaleGivesEveryChannelTheLuma() {
        assertTrue(ColorGrade.matrix(0, 5, 5, 5, true, m));
        float[] red = apply(1, 0, 0);
        for (float c : red) assertEquals(ColorGrade.LR, c, 1e-5);
    }

    @Test
    void hueAndSaturationKeepGray() {
        assertTrue(ColorGrade.matrix(3.7, 8, 5, 5, false, m));
        for (float c : apply(0.4F, 0.4F, 0.4F)) assertEquals(0.4F, c, 1e-5);
    }

    @Test
    void halfTurnMakesRedCyan() {
        ColorGrade.matrix(5, 5, 5, 5, false, m);
        float[] red = apply(1, 0, 0);
        assertTrue(red[0] < red[1] && red[0] < red[2], "red turned half way round reads cyan");
    }

    @Test
    void saturationScalesChroma() {
        ColorGrade.matrix(0, 10, 5, 5, false, m);
        float luma = 0.6F * ColorGrade.LR + 0.4F * (ColorGrade.LG + ColorGrade.LB);
        assertEquals(luma + 2 * (0.6F - luma), apply(0.6F, 0.4F, 0.4F)[0], 1e-5);
    }

    @Test
    void contrastAndBrightness() {
        ColorGrade.matrix(0, 5, 5, 10, false, m);
        assertEquals(1F, apply(0.75F, 0.75F, 0.75F)[0], 1e-5); // contrast 10 doubles the distance from mid gray
        ColorGrade.matrix(0, 5, 10, 5, false, m);
        assertEquals(0.7F, apply(0.2F, 0.2F, 0.2F)[0], 1e-5); // brightness 10 adds half
    }

    @Test
    void vibranceFollowsTheSlider() {
        ColorSaturationModule module = new ColorSaturationModule();
        ColorGrade grade = new ColorGrade();
        module.vibrance.setValue(10);
        grade.update(module);
        assertEquals(1F, grade.vibrance, 1e-6);
        assertTrue(grade.active, "vibrance alone still draws");
        module.grayscale.set(true);
        grade.update(module);
        assertEquals(0F, grade.vibrance, 0);
    }
}
