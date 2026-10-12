package com.thelads.core.client;

import com.thelads.core.modules.WavyCapesModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WavyCapeTest {
    private static WavyCapesModule settings(int wind) {
        var module = new WavyCapesModule();
        module.wind.setValue(wind);
        return module;
    }

    /** Runs the cape for `ticks` ticks with this movement per tick, facing yaw 0 (forward is +z). */
    private static WavyCape run(WavyCapesModule settings, int ticks, double dx, double dy, double dz, float lean) {
        var cape = new WavyCape();
        for (int t = 0; t <= ticks; t++) cape.update(settings, t, dx, dy, dz, 0, lean);
        return cape;
    }

    private static float min(WavyCape cape) {
        float min = 999;
        for (int i = 0; i < cape.segments(); i++) min = Math.min(min, cape.linkDegrees(i));
        return min;
    }

    private static float max(WavyCape cape) {
        float max = -999;
        for (int i = 0; i < cape.segments(); i++) max = Math.max(max, cape.linkDegrees(i));
        return max;
    }

    @Test void standingStillWithoutWindItHangsAtVanillasSixDegrees() {
        var cape = run(settings(0), 60, 0, 0, 0, 0);
        assertEquals(16, cape.segments());
        assertEquals(6, min(cape), 0.01);
        assertEquals(6, max(cape), 0.01);
    }

    @Test void runningForwardLiftsItLikeVanillasRunningCape() {
        float walking = max(run(settings(0), 60, 0, 0, 0.216, 0)), sprinting = max(run(settings(0), 60, 0, 0, 0.28, 0));
        assertTrue(walking > 30 && walking < 50, "walking " + walking);
        assertTrue(sprinting > walking && sprinting < 60, "sprinting " + sprinting);
        float backwards = max(run(settings(0), 60, 0, 0, -0.216, 0));
        assertEquals(6, backwards, 0.01, "walking backwards presses it to the legs, never into them");
    }

    @Test void fallingBlowsItUpButNeverPast110Degrees() {
        var cape = run(settings(0), 60, 0, -0.8, 0.1, 0);
        assertTrue(max(cape) > 90, "falling " + max(cape));
        assertTrue(max(cape) <= 110.01f);
        var jumping = run(settings(0), 60, 0, 0.42, 0.28, 0);
        assertTrue(max(jumping) < max(run(settings(0), 60, 0, 0, 0.28, 0)), "rising presses it down");
    }

    @Test void sneakingLeansItOutAsVanilla() {
        assertEquals(31, min(run(settings(0), 60, 0, 0, 0, 25)), 0.01);
    }

    @Test void windMakesItFlutterInWavesWithinTheLimits() {
        var settings = settings(100);
        var cape = new WavyCape();
        float low = 999, high = -999, tipLow = 999, tipHigh = -999;
        for (int t = 0; t <= 200; t++) {
            cape.update(settings, t, 0, 0, 0, 0, 0);
            if (t < 40) continue;
            low = Math.min(low, min(cape));
            high = Math.max(high, max(cape));
            tipLow = Math.min(tipLow, cape.linkDegrees(15));
            tipHigh = Math.max(tipHigh, cape.linkDegrees(15));
        }
        assertTrue(low >= 5.99f && high <= 110.01f, low + ".." + high);
        assertTrue(tipHigh - tipLow > 5, "the tip moves with the gusts: " + tipLow + ".." + tipHigh);
        float spread = max(cape) - min(cape);
        assertTrue(spread > 1, "links bend differently (a wave), spread " + spread);
    }

    @Test void strafingAndTurningSwayItSidewaysWithinTwelveDegrees() {
        var settings = settings(0);
        var cape = new WavyCape();
        for (int t = 0; t <= 60; t++) cape.update(settings, t, 0.3, 0, 0, 0, 0);
        assertTrue(Math.abs(cape.sway(1)) > 5 && Math.abs(cape.sway(1)) <= 12.01f, "strafing " + cape.sway(1));
        var turning = new WavyCape();
        for (int t = 0; t <= 60; t++) turning.update(settings, t, 0, 0, 0, t * 40, 0); // spinning, across the -180/180 seam
        assertTrue(Math.abs(turning.sway(1)) <= 12.01f);
        assertTrue(turning.sway(1) > 5, "turning right swings it like moving left: " + turning.sway(1));
    }

    @Test void aLongGapOrANewSegmentCountStartsItHangingAgain() {
        var settings = settings(0);
        var cape = run(settings, 60, 0, -0.8, 0, 0);
        cape.update(settings, 60 + WavyCape.MAX_GAP + 1, 0, -0.8, 0, 0, 0);
        assertEquals(6, max(cape), 0.01);
        settings.segments.setValue(8);
        cape.update(settings, 82, 0, -0.8, 0, 0, 0);
        assertEquals(8, cape.segments());
        assertEquals(6, max(cape), 0.01);
    }

    @Test void theMeshIsVanillasCapeBoxCutIntoStripsSmoothOrBlocky() {
        var settings = settings(0);
        var cape = run(settings, 30, 0, 0, 0, 0);
        assertEquals(4 * 16 + 2, cape.build(settings, 0.5f), "Smooth: four faces per strip, one top and one bottom edge");
        float minY = 99, maxY = -99, minZ = 99, maxU = 0, maxV = 0;
        for (int k = 0; k < cape.quads * 4; k++) {
            int at = k * WavyCape.STRIDE;
            for (int f = 0; f < WavyCape.STRIDE; f++) assertTrue(Float.isFinite(cape.mesh[at + f]));
            minY = Math.min(minY, cape.mesh[at + 1]);
            maxY = Math.max(maxY, cape.mesh[at + 1]);
            minZ = Math.min(minZ, cape.mesh[at + 2]);
            maxU = Math.max(maxU, cape.mesh[at + 3]);
            maxV = Math.max(maxV, cape.mesh[at + 4]);
            assertEquals(5 / 16f, Math.abs(cape.mesh[at]), 1e-5, "10 pixels wide");
            float nx = cape.mesh[at + 5], ny = cape.mesh[at + 6], nz = cape.mesh[at + 7];
            assertEquals(1, nx * nx + ny * ny + nz * nz, 1e-4, "unit normals");
        }
        // hangs from the shoulders, 16 pixels long and 1 thick, leaning 6 degrees out (the outer top corner a tenth of a pixel up)
        assertEquals(-Math.sin(Math.toRadians(6)) / 16, minY, 1e-5);
        assertEquals(Math.cos(Math.toRadians(6)), maxY, 1e-4);
        assertTrue(minZ >= -1e-6, "nothing in front of the back");
        assertEquals(22 / 64f, maxU, 1e-6, "the cape's own texture pixels only");
        assertEquals(17 / 32f, maxV, 1e-6);
        // the first quad is the outer face of the top strip: vanilla's u 11 at frame x -5 (box +5), v from 1
        assertEquals(-5 / 16f, cape.mesh[0], 1e-6);
        assertEquals(11 / 64f, cape.mesh[3], 1e-6);
        assertEquals(1 / 32f, cape.mesh[4], 1e-6);
        assertEquals(Math.cos(Math.toRadians(6)), cape.mesh[7], 1e-4, "the outer face faces away from the back");
        settings.style.setIndex(1);
        assertEquals(6 * 16, cape.build(settings, 0.5f), "Blocky: a closed box per strip");
    }

    @Test void otherPlayersCapesFollowTheirOption() {
        var module = (WavyCapesModule) com.thelads.core.config.ModuleManager.getInstance().getModule(WavyCapesModule.NAME);
        boolean was = module.others.get(), on = module.isEnabled();
        try {
            module.setEnabled(true);
            module.others.set(false);
            assertSame(module, WavyCapesModule.active(true));
            assertNull(WavyCapesModule.active(false));
            module.others.set(true);
            assertSame(module, WavyCapesModule.active(false));
            module.setEnabled(false);
            assertNull(WavyCapesModule.active(true));
        } finally {
            module.others.set(was);
            module.setEnabled(on);
        }
    }
}
