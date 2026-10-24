package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.modules.MenuBlurModule;
import org.junit.jupiter.api.Test;

class ScreenEffectsTest {
    private static final long SIXTIETH = 16_666_667L;

    @Test
    void strengthIsWhatStaysPerSixtiethOfASecond() {
        assertEquals(0.45F, ScreenEffects.motionRetain(5, SIXTIETH), 1e-4);
        assertEquals(0.09F, ScreenEffects.motionRetain(1, SIXTIETH), 1e-4);
    }

    @Test
    void sameTrailAtAnyFrameRate() {
        float at120 = ScreenEffects.motionRetain(5, SIXTIETH / 2);
        assertEquals(0.45F, at120 * at120, 1e-4);
    }

    @Test
    void neverKeepsSoMuchThatGhostsStay() {
        assertEquals(ScreenEffects.MAX_RETAIN, ScreenEffects.motionRetain(10, SIXTIETH / 10), 0);
        assertTrue(ScreenEffects.motionRetain(10, SIXTIETH) <= ScreenEffects.MAX_RETAIN);
    }

    @Test
    void firstFrameAndStallsKeepNothing() {
        assertEquals(0F, ScreenEffects.motionRetain(10, 0), 0);
        assertEquals(0F, ScreenEffects.motionRetain(10, 400_000_000L), 0);
    }

    @Test
    void menuBlurPerKind() {
        MenuBlurModule blur = new MenuBlurModule();
        assertEquals(4, blur.radius(ScreenEffects.INVENTORY));
        blur.pause.set(false);
        assertEquals(0, blur.radius(ScreenEffects.PAUSE));
        assertEquals(4, blur.radius(ScreenEffects.OTHER));
        blur.setEnabled(false);
        assertEquals(0, blur.radius(ScreenEffects.OTHER));
    }
}
