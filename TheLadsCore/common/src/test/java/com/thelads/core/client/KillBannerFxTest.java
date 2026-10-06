package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerFx;
import com.thelads.core.client.killbanner.KillBannerStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The game's FX flipbooks (killbanner/fx.properties, fx-skins.properties). */
class KillBannerFxTest {
    @Test
    void theGamesFlipbooksAreThere() {
        assertNotNull(KillBannerFx.FLAME, "the hero flame on every kill");
        assertEquals(35, KillBannerFx.FLAME.fps(), .01);
        assertEquals(20, KillBannerFx.FLAME.frames().length, "20 frames: 0.57 s");
        assertNotNull(KillBannerFx.LARGE_SPARKS);
        assertNotNull(KillBannerFx.X_SPARKS);
        var t1 = KillBannerFx.book("baset1_fx");
        assertNotNull(t1);
        assertEquals(40, t1.fps(), .01);
        assertEquals(256, t1.cellW());
        assertEquals(49, t1.frames().length, "49 frames at 40 fps: 1.23 s");
        assertEquals(-1, t1.frames()[0], "it opens on a blank frame");
        assertTrue(t1.frames()[10] >= 0 && t1.frames()[10] < 20 * t1.cols(), "then atlas cells");
    }

    @Test
    void aFlipbookPlaysOneShotAFramePerTickFromItsFirstTimerTick() {
        var t1 = KillBannerFx.book("baset1_fx");
        float d = KillBannerFx.FIRST_TICK;
        assertEquals(.05f, d, 1e-6, "the game shows the first frame 50 ms after the FX event (measured on Oni's preview)");
        assertEquals(-1, t1.cell(-.1f), "not yet");
        assertEquals(-1, t1.cell(d - .001f), "nothing until the first timer tick");
        assertEquals(t1.frames()[0], t1.cell(d));
        assertEquals(t1.frames()[10], t1.cell(d + 10 / 40f + .001f), "frame 10 a quarter second after that");
        assertEquals(t1.frames()[48], t1.cell(d + 48 / 40f + .001f));
        assertEquals(-1, t1.cell(d + t1.seconds()), "over after its last frame");
        assertEquals(1.225, t1.seconds(), .001);
    }

    @Test
    void eachKillCountHasItsTier() {
        assertNull(KillBannerFx.tier(KillBannerStyle.ONI, 0, 1), "one kill: no sparks (the flame only)");
        assertEquals("baset1_fx", KillBannerFx.tier(KillBannerStyle.ONI, 0, 2).name(), "2 and 3 kills: tier 1");
        assertEquals("baset1_fx", KillBannerFx.tier(KillBannerStyle.ONI, 0, 3).name());
        assertEquals("baset2_fx", KillBannerFx.tier(KillBannerStyle.ONI, 0, 4).name(), "4 kills: tier 2");
        assertEquals("baset3_fx", KillBannerFx.tier(KillBannerStyle.ONI, 0, 5).name(), "the ace: tier 3");
        assertEquals("baset3_fx", KillBannerFx.tier(KillBannerStyle.ONI, 0, 9).name());
        assertEquals("sparks_t1_fb", KillBannerFx.tier(KillBannerStyle.SOVEREIGN, 2, 2).name(), "Sovereign plays the 256 px sparks set");
        assertEquals("sparkslg_t1", KillBannerFx.tier(KillBannerStyle.CHAMPIONS2024, 0, 3).name());
        assertEquals("ego2_audiocircle_raja", KillBannerFx.tier(KillBannerStyle.ORABYONETAP_RAJA, 0, 4).name(), "ORA by OneTap plays its circle for every tier");
        assertEquals("baset2_fx", KillBannerFx.tier(null, 0, 4).name(), "no skin: the Base tiers");
    }
}
