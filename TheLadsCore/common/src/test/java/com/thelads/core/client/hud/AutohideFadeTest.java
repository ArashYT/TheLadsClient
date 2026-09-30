package com.thelads.core.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AutohideFadeTest {
    /** Frames until opacity reaches target; like the game, the hotbar and the Lads HUD each step once per frame, 0.02 ms apart. */
    private static int frames(float opacity, float target, int fps) {
        double frame = 1.0 / fps;
        for (int i = 1; i <= fps * 2; i++) {
            opacity = AutohideFade.step(opacity, target, frame - 2e-5, .35);
            opacity = AutohideFade.step(opacity, target, 2e-5, .35);
            if (opacity == target) return i;
        }
        return -1;
    }
    @Test void defaultFadeFinishesInAboutItsDurationAtAnyFrameRate() {
        for (int fps : new int[]{30, 144, 567, 1000}) {
            int in = frames(0, 1, fps), out = frames(1, 0, fps);
            assertTrue(in >= .3 * fps && in <= .4 * fps, fps + " FPS fade-in took " + in + " frames");
            assertTrue(out >= .3 * fps && out <= .4 * fps, fps + " FPS fade-out took " + out + " frames");
        }
    }
    @Test void instantFadeAndLongFramesStayBounded() {
        assertEquals(0, AutohideFade.step(1, 0, 0, 0));
        assertEquals(1, AutohideFade.step(0, 1, 0, 0));
        assertEquals(.1f / .35f, AutohideFade.step(0, 1, 5, .35), 1e-6f);
    }
}
