package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Kingdom Archives skins' drawn animation (KillBannerPlayer.layers): its timing and easing. */
class KillBannerMotionTest {
    private static final KillBannerStyle SKIN = KillBannerStyle.AEMONDIR;

    private static KillBannerPlayer.Layers at(KillBannerStyle style, int kills, double frame, double seconds) {
        return KillBannerPlayer.layers(style, kills, frame / 60, seconds, false);
    }

    @Test
    void theEmblemDropsInThenTheFrameRingAndPipsLand() {
        var start = at(SKIN, 1, 1, 2);
        assertTrue(start.emblemY() < -20 && start.emblemScale() < 1, "the emblem starts small and above its place");
        assertEquals(0, start.frameAlpha());
        assertEquals(0, start.pipAlpha());
        assertTrue(at(SKIN, 1, 3, 2).emblemScale() > 1.05f, "it overshoots its size");
        var sat = at(SKIN, 1, 9, 2);
        assertEquals(0, sat.emblemY(), .01f);
        assertEquals(1, sat.emblemScale(), .001f);
        assertTrue(at(SKIN, 1, 7, 2).frameAlpha() == 0 && at(SKIN, 1, 7, 2).ringAlpha() == 0, "nothing but the emblem before frame 8");
        var landing = at(SKIN, 1, 12, 2);
        assertTrue(landing.frameScale() > 1 && landing.ringScale() < 1 && landing.pipScale() > 1 && landing.pipRadius() > 1,
            "the frame zooms in, the ring opens out, the pips fly in");
        assertTrue(landing.pipFlare() > .9f, "the pips flare as the mark lands");
        var settled = at(SKIN, 1, 30, 2);
        assertEquals(1, settled.frameAlpha());
        assertEquals(1, settled.ringAlpha());
        assertEquals(1, settled.pipAlpha());
        assertEquals(1, settled.frameScale(), .001f);
        assertEquals(1, settled.ringScale(), .001f);
        assertEquals(1, settled.pipScale(), .001f);
        assertEquals(1, settled.pipRadius(), .001f);
        assertEquals(0, settled.pipFlare());
        assertEquals(0, settled.burstAlpha());
    }

    @Test
    void motionIsContinuous() {
        // No jumps between 120 fps frames, so it reads as motion, not a cut.
        for (int kills = 1; kills <= 5; kills++) {
            var before = at(SKIN, kills, 0, 3);
            for (double f = .5; f < 60 * KillBannerPlayer.stillSeconds(kills); f += .5) {
                var now = at(SKIN, kills, f, 3);
                if (now == null) break;
                assertEquals(before.emblemY(), now.emblemY(), 6, "k" + kills + " emblem at " + f);
                assertEquals(before.frameScale(), now.frameScale(), .05, "k" + kills + " frame at " + f);
                assertEquals(before.pipSpin(), now.pipSpin(), 12, "k" + kills + " spin at " + f);
                before = now;
            }
        }
    }

    @Test
    void moreKillsMoveMore() {
        assertTrue(at(SKIN, 1, 40, 2).spray() < 0, "one kill throws no spray (as in Reaver's and Rogue's footage)");
        assertEquals(0, KillBannerPlayer.sprayCount(1));
        for (int kills = 2; kills <= 5; kills++) {
            assertTrue(at(SKIN, kills, 40, 5).spray() > 0, kills + " kills throw a spray");
            assertTrue(KillBannerPlayer.sprayCount(kills) > KillBannerPlayer.sprayCount(kills - 1) || kills == 2);
            assertTrue(at(SKIN, kills, 71, 5).glintAlpha() > .9f, kills + " kills: a glint runs round the ring");
        }
        assertEquals(0, at(SKIN, 1, 71, 2).glintAlpha());
        assertTrue(KillBannerPlayer.stillSeconds(5) > 3 && KillBannerPlayer.stillSeconds(1) < 1.2, "the ace plays longest");
        // The ace's pips turn once round and end where they began.
        assertEquals(0, at(SKIN, 4, 150, 5).pipSpin());
        float turning = at(SKIN, 5, 155, 5).pipSpin();
        assertTrue(turning > 90 && turning < 270, "half way round at " + turning);
        assertEquals(360, at(SKIN, 5, 200, 5).pipSpin(), .01f);
        assertTrue(at(SKIN, 5, 120, 5).glintAlpha() > 0, "the ace's glint keeps running");
    }

    @Test
    void holdsForTheDurationThenLeavesLikeRogue() {
        double seconds = 3;
        int exit = (int) Math.round(seconds * 60) - 16;
        var held = at(SKIN, 1, exit - 1, seconds);
        assertEquals(1, held.frameAlpha());
        assertEquals(1, held.emblemShade());
        // The frame goes first, then the emblem shrinks and darkens, then the ring, then the pips.
        var going = at(SKIN, 1, exit + 4, seconds);
        assertEquals(0, going.frameAlpha());
        assertTrue(going.emblemScale() < 1 && going.emblemShade() < 1 && going.emblemAlpha() > .9f);
        assertEquals(1, going.ringAlpha());
        var late = at(SKIN, 1, exit + 13, seconds);
        assertEquals(0, late.emblemAlpha());
        assertEquals(0, late.ringAlpha());
        assertTrue(late.pipAlpha() > 0, "the pips go last");
        assertNull(at(SKIN, 1, exit + 16, seconds), "then it is gone");
        assertNotNull(at(SKIN, 1, 60 * KillBannerPlayer.stillSeconds(1) - 1, .5), "a short duration still plays it all");
        assertNull(KillBannerPlayer.layers(SKIN, 1, -1, 2, false));
        assertNull(KillBannerPlayer.layers(SKIN, 1, Double.NaN, 2, false));
    }

    @Test
    void theMarkAndStrobeMatchTheStrips() {
        int m = KillBannerStyle.MARK_FRAME;
        assertEquals(0, at(SKIN, 1, m - .5, 2).markSize());
        var landing = at(SKIN, 1, m + .5, 2);
        assertTrue(landing.markSize() > SKIN.markSize * 2 && landing.markColor() == 0xFFFFFFFF, "lands large and white");
        assertEquals(1f, at(SKIN, 1, m + 5.5, 2).strobe(), "the strobe peaks as the strips' does");
        var settled = at(SKIN, 1, 45, 2);
        assertEquals(SKIN.markSize, settled.markSize(), .05f);
        assertEquals(KillBannerPlayer.MARK_RED, settled.markColor());
        assertEquals(1, KillBannerPlayer.layers(SKIN, 1, 45 / 60.0, 2, true).labelAlpha(), "HEADSHOT for a head kill");
    }

    @Test
    void bannerSwapTurnsThePreviousKillsArtIntoThisOne() {
        KillBannerStyle swap = KillBannerStyle.CHAMPIONS2024;
        assertEquals(0, at(swap, 3, 5, 2).tier(), "the two-kill art first");
        assertEquals(1, at(swap, 3, 14, 2).tier(), "then the three-kill art");
        assertTrue(at(swap, 3, 11, 2).emblemScale() > 1.05f, "with a punch");
        assertEquals(1, at(swap, 1, 5, 2).tier(), "one kill has nothing before it");
        assertEquals(1, at(SKIN, 3, 5, 2).tier());
    }

    @Test
    void theSprayFliesOutAndFades() {
        float[] p = new float[6];
        int shown = 0;
        for (int i = 0; i < KillBannerPlayer.sprayCount(5); i++) {
            assertFalse(KillBannerPlayer.particle(i, 0, p), "nothing at its start");
            assertFalse(KillBannerPlayer.particle(i, 1, p), "all gone after a second");
            for (float t = .02f; t < 1; t += .02f) {
                if (!KillBannerPlayer.particle(i, t, p)) continue;
                shown++;
                assertTrue(Math.abs(p[0]) < 3 && p[1] > -1.5 && p[1] < 2, "particle " + i + " stays near the banner: " + p[0] + ", " + p[1]);
                assertTrue(p[4] >= 0 && p[4] <= 1 && p[2] > p[3] && p[3] > 0);
            }
        }
        assertTrue(shown > 300);
        // Both ways: even particles to the right, odd to the left.
        assertTrue(KillBannerPlayer.particle(0, .3f, p) && p[0] > 0 && KillBannerPlayer.particle(1, .3f, p) && p[0] < 0);
    }

    @Test
    void thePreviewPlaysEveryKillCountInTurn() {
        assertEquals(1, KillBannerPlayer.previewKills(0));
        assertEquals(0, KillBannerPlayer.previewAge(0), 1e-9);
        double t = 0;
        for (int kills = 1; kills <= 5; kills++) {
            assertEquals(kills, KillBannerPlayer.previewKills(t + .1));
            assertEquals(.1, KillBannerPlayer.previewAge(t + .1), 1e-6);
            double shown = KillBannerPlayer.previewSeconds(kills);
            assertNotNull(KillBannerPlayer.layers(SKIN, kills, shown - .05, shown, false));
            assertNull(KillBannerPlayer.layers(SKIN, kills, shown + .05, shown, false), "a gap before the next");
            t += shown + .4;
        }
        assertEquals(1, KillBannerPlayer.previewKills(t + .1), "and round again");
    }

    @Test
    void everyStillSkinHasItsAccentColours() throws Exception {
        Properties accents = new Properties();
        try (InputStream in = KillBannerMotionTest.class.getResourceAsStream("/assets/theladscore/killbanner/accent.properties")) {
            accents.load(in);
        }
        for (KillBannerStyle style : KillBannerStyle.values()) {
            if (style.isAnimated()) continue;
            String colours = accents.getProperty(style.id);
            assertNotNull(colours, style.id + " has accent colours");
            int expected = style.type == KillBannerStyle.Type.BANNER_SWAP ? 1 : style.variantNames.length;
            assertEquals(expected, colours.split(",").length, style.id + ": one per variant");
        }
        assertEquals(0xFFB63A, KillBannerStyle.AEMONDIR.accent(0), "Aemondir's gold pip");
        assertEquals(KillBannerStyle.AEMONDIR.accent(3), KillBannerStyle.AEMONDIR.accent(9), "past the last variant: the last");
        assertEquals(0xFFFFFF, KillBannerStyle.REAVER.accent(0), "the strips need none");
    }

    @Test
    void qaCanHoldABannerAtAMoment() {
        var timeline = new KillBannerTimeline();
        timeline.trigger(3, 1_000L, false);
        timeline.freeze(.5);
        assertEquals(.5, timeline.age(99_000_000_000L));
        timeline.trigger(1, 2_000L, false);
        assertEquals(1e-6, timeline.age(3_000L), 1e-12, "a new kill plays again");
        timeline.freeze(.2);
        timeline.clear();
        assertTrue(timeline.age(3_000L) < 0);
    }
}
