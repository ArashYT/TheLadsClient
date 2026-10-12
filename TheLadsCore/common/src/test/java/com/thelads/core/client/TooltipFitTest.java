package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TooltipFitTest {
    @Test void fittingTooltipsKeepTheirSize() {
        assertEquals(1F, TooltipFit.scale(150, 100, 480, 270, 4));
        assertEquals(1F, TooltipFit.scale(470, 260, 480, 270, 4), "exactly the screen, with its frame");
    }

    @Test void tallTooltipsShrinkToTheScreenHeight() {
        float s = TooltipFit.scale(150, 400, 480, 270, 4);
        assertEquals(270F / 410F, s, 1e-5);
        assertTrue((400 + TooltipFit.PAD) * s <= 270.001F, "the box fits");
    }

    @Test void wideTooltipsShrinkToTheScreenWidth() {
        assertEquals(480F / 610F, TooltipFit.scale(600, 40, 480, 270, 4), 1e-5);
    }

    @Test void neverBelowHalfNorBelowOneScreenPixel() {
        assertEquals(1F, TooltipFit.scale(150, 2000, 1920, 1080, 1), "GUI scale 1: a GUI pixel is already one screen pixel");
        assertEquals(0.5F, TooltipFit.scale(150, 2000, 480, 270, 4));
        assertEquals(0.5F, TooltipFit.last, "QA reads the last scale");
    }
}
