package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import org.junit.jupiter.api.Test;

class ZoomModuleTest {
    private static final long MS = 1_000_000L;

    private static ZoomModule zoom() {
        ZoomModule zoom = new ZoomModule();
        zoom.setEnabled(true);
        return zoom;
    }

    /** Factor after rendering frames every {@code frameMs} from {@code start} until {@code end} (ms). */
    private static float run(ZoomModule zoom, long start, long end, double frameMs) {
        float factor = 1;
        for (double t = start; t <= end; t += frameMs) factor = zoom.fovFactor(false, (long) (t * MS));
        return factor;
    }

    @Test
    void smoothZoomIsFrameRateIndependentAndNeverOvershoots() {
        float[] at150 = new float[3];
        double[] frames = {1000 / 30.0, 1000 / 144.0, 1000 / 1000.0};
        for (int i = 0; i < frames.length; i++) {
            ZoomModule zoom = zoom();
            zoom.fovFactor(false, 1); // first frame: no time has passed
            zoom.key(true);
            float previous = 1;
            for (double t = frames[i]; t <= 150; t += frames[i]) {
                float factor = zoom.fovFactor(false, (long) (t * MS));
                assertTrue(factor <= previous && factor >= ZoomModule.DEFAULT_ZOOM, "zooming in only narrows, never past the target");
                previous = factor;
            }
            at150[i] = zoom.fovFactor(false, 150 * MS);
        }
        assertEquals(at150[0], at150[1], 1e-4, "30 and 144 FPS show the same zoom at the same moment");
        assertEquals(at150[1], at150[2], 1e-4, "144 and 1000 FPS show the same zoom at the same moment");
        assertEquals(ZoomModule.DEFAULT_ZOOM * Math.pow(4, Math.exp(-ZoomModule.RATE * 0.15)), at150[0], 1e-4);
    }

    @Test
    void settlesExactlyAndRestoresTheVanillaFov() {
        ZoomModule zoom = zoom();
        zoom.fovFactor(false, 1);
        zoom.key(true);
        assertEquals(ZoomModule.DEFAULT_ZOOM, run(zoom, 0, 1000, 7), "lands exactly on 4x");
        zoom.key(false);
        float previous = ZoomModule.DEFAULT_ZOOM;
        for (long t = 1007; t < 2000; t += 7) {
            float factor = zoom.fovFactor(false, t * MS);
            assertTrue(factor >= previous && factor <= 1, "zooming out only widens, never past 1x");
            previous = factor;
        }
        assertEquals(1f, previous, "back to exactly 1x: the FOV is vanilla's again");
        assertEquals(1f, zoom.sensitivity());
    }

    @Test
    void releasingMidAnimationReversesWithoutAJump() {
        ZoomModule zoom = zoom();
        zoom.fovFactor(false, 1);
        zoom.key(true);
        float mid = run(zoom, 0, 60, 5);
        zoom.key(false);
        float next = zoom.fovFactor(false, 65 * MS);
        assertTrue(next > mid && next - mid < 0.1f, "the zoom-out starts where the zoom-in was (" + mid + " -> " + next + ")");
    }

    @Test
    void smoothZoomOffSnaps() {
        ZoomModule zoom = zoom();
        ((BoolOption) zoom.getOption("Smooth Zoom")).set(false);
        zoom.key(true);
        assertEquals(ZoomModule.DEFAULT_ZOOM, zoom.fovFactor(false, 5 * MS));
        zoom.key(false);
        assertEquals(1f, zoom.fovFactor(false, 6 * MS));
    }

    @Test
    void scrollIsConsumedOnlyWhileZoomedAndStaysInRange() {
        ZoomModule zoom = zoom();
        ((BoolOption) zoom.getOption("Smooth Zoom")).set(false);
        assertFalse(zoom.scroll(1), "unzoomed, the scroll goes to the hotbar");
        zoom.key(true);
        assertTrue(zoom.scroll(1));
        assertEquals(ZoomModule.DEFAULT_ZOOM / 1.25f, zoom.fovFactor(false, 1), 1e-6, "one notch up zooms in 25%");
        assertTrue(zoom.scroll(-2));
        assertEquals(ZoomModule.DEFAULT_ZOOM * 1.25f, zoom.fovFactor(false, 2), 1e-6);
        for (int i = 0; i < 40; i++) zoom.scroll(1);
        assertEquals(ZoomModule.MIN_ZOOM, zoom.fovFactor(false, 3));
        for (int i = 0; i < 40; i++) zoom.scroll(-1);
        assertEquals(ZoomModule.MAX_ZOOM, zoom.fovFactor(false, 4));
        assertFalse(zoom.scroll(0));
        ((BoolOption) zoom.getOption("Scroll to Zoom")).set(false);
        assertFalse(zoom.scroll(1), "Scroll to Zoom off: the hotbar keeps the scroll");
        zoom.key(false);
        zoom.key(true);
        assertEquals(ZoomModule.DEFAULT_ZOOM, zoom.fovFactor(false, 5), "the next zoom starts at the default again");
    }

    @Test
    void toggleModeAndRelease() {
        ZoomModule zoom = zoom();
        ((DropdownOption) zoom.getOption("Mode")).setIndex(1);
        zoom.key(true);
        zoom.key(false);
        assertTrue(zoom.isActive(), "toggle: the zoom stays after the key comes up");
        zoom.key(true);
        zoom.key(false);
        assertFalse(zoom.isActive(), "a second press zooms out");
        zoom.key(true);
        zoom.release();
        assertFalse(zoom.isActive(), "a screen or focus loss ends it");
    }

    @Test
    void handZoomAndDisabled() {
        ZoomModule zoom = zoom();
        ((BoolOption) zoom.getOption("Smooth Zoom")).set(false);
        zoom.key(true);
        assertEquals(ZoomModule.DEFAULT_ZOOM, zoom.fovFactor(true, 1), "Hand Zoom on: the held item zooms too");
        ((BoolOption) zoom.getOption("Hand Zoom")).set(false);
        assertEquals(1f, zoom.fovFactor(true, 2), "Hand Zoom off: the held item keeps its FOV");
        assertEquals(ZoomModule.DEFAULT_ZOOM, zoom.sensitivity(), "the mouse turns 4x slower at 4x");
        zoom.setEnabled(false);
        assertEquals(1f, zoom.fovFactor(false, 3));
        assertEquals(1f, zoom.sensitivity());
        zoom.key(true);
        zoom.setEnabled(true);
        assertEquals(1f, zoom.fovFactor(false, 4), "a key pressed while Zoom was off does not zoom");
    }
}
