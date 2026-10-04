package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodPreviewTest {
    @Test void eatingCapsHungerAtTwentyAndSaturationAtHunger() {
        assertEquals(14, FoodPreview.foodAfter(10, 4));
        assertEquals(20, FoodPreview.foodAfter(18, 8));
        assertEquals(6.5f, FoodPreview.saturationAfter(4, 2.5f, 14));
        assertEquals(14f, FoodPreview.saturationAfter(3, 14.4f, 14));
        assertEquals(0f, FoodPreview.saturationAfter(-5, 1, 3));
    }

    @Test void iconFillCoversTwoPointsPerIcon() {
        assertEquals(1f, FoodPreview.iconFill(5, 0));
        assertEquals(1f, FoodPreview.iconFill(5, 1));
        assertEquals(0.5f, FoodPreview.iconFill(5, 2));
        assertEquals(0f, FoodPreview.iconFill(5, 3));
        assertEquals(0.2f, FoodPreview.iconFill(2.4f, 1), 1e-6);
    }

    @Test void pulseRunsZeroToOneAndBack() {
        assertEquals(0f, FoodPreview.pulse(0), 1e-6);
        assertEquals(1f, FoodPreview.pulse(12), 1e-6);
        assertEquals(0f, FoodPreview.pulse(24), 1e-6);
    }

    @Test void legacyRegenerationHealsOnePerThreeExhaustionDownToEighteenHunger() {
        assertEquals(11f, FoodPreview.regenerated(20, 5, 0, 100, true));
        assertEquals(0f, FoodPreview.regenerated(17, 20, 0, 100, true));
        assertEquals(3f, FoodPreview.regenerated(20, 5, 0, 3, true), "stops once the missing health is back");
    }

    @Test void modernRegenerationSpendsSaturationFastThenFallsBackToSlowHealing() {
        assertEquals(5.5f, FoodPreview.regenerated(20, 5, 0, 100, false), 1e-4);
        // At 19 hunger there is no fast healing: one per 6 exhaustion until hunger drops below 18 (saturation first).
        assertEquals(3f, FoodPreview.regenerated(19, 2, 0, 100, false), 1e-4);
        assertEquals(0f, FoodPreview.regenerated(20, 5, 0, 0, false));
    }

    @Test void regenerationRejectsNonFiniteAndHugeValues() {
        assertEquals(0f, FoodPreview.regenerated(20, Float.NaN, 0, 10, false));
        assertEquals(0f, FoodPreview.regenerated(20, 5, Float.POSITIVE_INFINITY, 10, false));
        assertTrue(Float.isFinite(FoodPreview.regenerated(Integer.MAX_VALUE, Float.MAX_VALUE, -5, Float.MAX_VALUE, false)));
        assertTrue(FoodPreview.regenerated(20, 0.001f, 0, 100, false) > 0, "a tiny saturation crawl still ends");
    }

    @Test void regenerationEffectHealsOncePerInterval() {
        assertEquals(4f, FoodPreview.regenerationEffect(100, 1)); // golden apple: Regeneration II, 5 s
        assertEquals(200f, FoodPreview.regenerationEffect(600, 4)); // 1.8.9 enchanted golden apple: Regeneration V, 30 s
        assertEquals(40f, FoodPreview.regenerationEffect(40, 9), "past level 6 it heals every tick");
    }

    @Test void debugLineMarksWhatTheServerDoesNotSend() {
        assertEquals("Food: 14 hunger, 3.50 saturation, 1.25/4 exhaustion", FoodPreview.debugLine(14, 3.5f, 1.25f, true));
        assertEquals("Food: 14 hunger, 3.50? saturation, ?/4 exhaustion", FoodPreview.debugLine(14, 3.5f, 0, false));
        assertEquals("2.4", FoodPreview.number(2.4f));
        assertEquals("4", FoodPreview.number(4f));
    }
}
