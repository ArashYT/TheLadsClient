package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBannerTemplate;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The still skins' animation (KillBannerPlayer.layers): Rogue's measured motion (KillBannerTemplate) on their layers. */
class KillBannerMotionTest {
    private static final KillBannerStyle SKIN = KillBannerStyle.AEMONDIR;

    private static KillBannerPlayer.Layers at(KillBannerStyle style, int kills, double frame, double seconds) {
        return KillBannerPlayer.layers(style, kills, frame / 60, seconds, false);
    }

    @Test
    void theTemplateIsRoguesFrames() {
        // What tools/killbanner/gen_template.py measured from the shipped Rogue strips: their lengths and their way out.
        int[] introEnd = {51, 81, 88, 77, 222}, exit = {16, 16, 16, 16, 14};
        for (int kills = 1; kills <= 5; kills++) {
            KillBannerTemplate t = KillBannerTemplate.of(kills);
            assertEquals(introEnd[kills - 1], t.introEnd, "k" + kills + " holds where Rogue's strip holds");
            assertEquals(exit[kills - 1], t.exit, "k" + kills + " leaves as Rogue's strip leaves");
            assertEquals(t.introEnd + 1 + t.exit, t.iconAlpha.length);
            assertEquals(0, t.iconAlpha[0], "nothing in the first frame");
            assertEquals(1, t.iconAlpha[2], "the icon pops in at frame 2");
            assertEquals(1, t.iconAlpha[t.introEnd]);
            assertEquals(1, t.ringAlpha[t.introEnd]);
            assertEquals(1, t.frameAlpha[t.introEnd]);
            assertEquals(1, t.pipAlpha[t.introEnd]);
            assertEquals(0, t.iconAlpha[t.introEnd + t.exit - 1], "the icon is gone by the last frame");
        }
        assertEquals(0, KillBannerTemplate.of(1).sprayCount, "one kill throws no droplets");
        for (int kills = 2; kills <= 5; kills++) assertTrue(KillBannerTemplate.of(kills).sprayCount >= 8, kills + " kills throw droplets");
        assertEquals(KillBannerTemplate.of(5), KillBannerTemplate.of(9), "past five kills: the ace");
    }

    @Test
    void theIconPopsInAndJumpsThenTheFrameAndTheRingFadeIn() {
        assertEquals(0, at(SKIN, 1, 1, 2).emblemAlpha(), "nothing yet");
        var popped = at(SKIN, 1, 2, 2);
        assertEquals(1, popped.emblemAlpha());
        assertEquals(1, popped.emblemScale(), .001f);
        assertEquals(0, popped.emblemY(), .001f, "it pops in at its place");
        assertEquals(0, popped.frameAlpha());
        assertEquals(0, popped.ringAlpha());
        var up = at(SKIN, 1, 4, 2);
        assertTrue(up.emblemY() < -25 && up.emblemScale() < .9f, "then jumps up, a little smaller: " + up.emblemY() + ", " + up.emblemScale());
        var back = at(SKIN, 1, 9, 2);
        assertEquals(0, back.emblemY(), .001f);
        assertEquals(1, back.emblemScale(), .001f);
        assertTrue(at(SKIN, 1, 8, 2).frameAlpha() > 0 && at(SKIN, 1, 8, 2).frameAlpha() < .2f, "the frame starts fading in at frame 8");
        assertEquals(1, at(SKIN, 1, 15, 2).frameAlpha(), "and is there by frame 15");
        assertTrue(at(SKIN, 1, 15, 2).ringAlpha() < .05f, "the ring is not there yet");
        assertTrue(at(SKIN, 1, 30, 2).ringAlpha() > .2f && at(SKIN, 1, 30, 2).ringAlpha() < .6f, "it fades in from frame 24");
        assertEquals(1, at(SKIN, 1, 40, 2).ringAlpha());
        assertEquals(1, at(SKIN, 1, 40, 2).ringScale(), .001f, "in place");
        assertEquals(1, at(SKIN, 1, 40, 2).frameScale(), .001f);
    }

    @Test
    void thePipsFadeInOutsideGlowAndSlideIn() {
        var early = at(SKIN, 1, 5, 2);
        assertTrue(early.pipAlpha() > .3f && early.pipAlpha() < .9f, "a dim pip from the start: " + early.pipAlpha());
        assertTrue(early.pipRadius() > 1.1f, "sitting out from its place: " + early.pipRadius());
        assertEquals(0, early.pipFlare(), "no glow yet");
        assertEquals(1, at(SKIN, 1, 11, 2).pipAlpha());
        var peak = at(SKIN, 1, 14, 2);
        assertTrue(peak.pipFlare() > .5f, "the glow peaks as the mark lands: " + peak.pipFlare());
        assertTrue(peak.pipRadius() > 1.1f);
        var sliding = at(SKIN, 1, 40, 2);
        assertTrue(sliding.pipRadius() > 1.02f && sliding.pipRadius() < 1.15f, "sliding in with the ring: " + sliding.pipRadius());
        assertEquals(0, sliding.pipFlare());
        assertEquals(1, at(SKIN, 1, 52, 2).pipRadius(), .001f, "in place when settled");
        assertEquals(0, at(SKIN, 1, 52, 2).pipSpin(), .001f);
        var turned = at(SKIN, 5, 200, 5);
        assertTrue(Math.abs(turned.pipSpin()) > 300, "the ace's pips go round the ring: " + turned.pipSpin());
        assertEquals(0, at(SKIN, 3, 200, 5).pipSpin(), .001f, "three kills' pips stay");
    }

    @Test
    void motionIsContinuous() {
        // No jumps between 120 fps frames, so it reads as motion, not a cut.
        for (int kills = 1; kills <= 5; kills++) {
            var before = at(SKIN, kills, 0, 3);
            for (double f = .5; f < 60 * KillBannerPlayer.stillSeconds(kills); f += .5) {
                var now = at(SKIN, kills, f, 3);
                if (now == null) break;
                assertEquals(before.emblemY(), now.emblemY(), 8, "k" + kills + " emblem at " + f);
                assertEquals(before.emblemScale(), now.emblemScale(), .08, "k" + kills + " emblem size at " + f);
                assertEquals(before.ringAlpha(), now.ringAlpha(), .3, "k" + kills + " ring at " + f); // it goes in 3 frames on the way out
                assertEquals(before.pipSpin(), now.pipSpin(), 16, "k" + kills + " spin at " + f); // the ace's pips peak at 30 degrees a frame
                before = now;
            }
        }
    }

    @Test
    void moreKillsThrowDroplets() {
        assertTrue(at(SKIN, 1, 40, 2).spray() < 0, "one kill throws no spray (as in Rogue's footage)");
        assertEquals(0, KillBannerPlayer.sprayCount(1));
        for (int kills = 2; kills <= 5; kills++) {
            int start = KillBannerTemplate.of(kills).sprayStart;
            assertTrue(at(SKIN, kills, start - 1, 5).spray() < 0);
            assertEquals(0, at(SKIN, kills, start, 5).spray(), 1e-6, kills + " kills throw a spray from frame " + start);
            assertTrue(KillBannerPlayer.sprayCount(kills) >= 8 && KillBannerPlayer.sprayCount(kills) <= 36);
        }
        assertTrue(KillBannerPlayer.stillSeconds(5) > 3 && KillBannerPlayer.stillSeconds(1) < 1.2, "the ace plays longest");
    }

    @Test
    void holdsForTheDurationThenLeavesLikeRogue() {
        double seconds = 3;
        int exit = (int) Math.round(seconds * 60) - KillBannerTemplate.of(1).exit;
        var held = at(SKIN, 1, exit - 1, seconds);
        assertEquals(1, held.frameAlpha());
        assertEquals(1, held.emblemShade());
        assertEquals(1, held.emblemScale(), .001f);
        // The frame goes first, then the emblem shrinks and darkens, then the ring, then the pips.
        var going = at(SKIN, 1, exit + 4, seconds);
        assertEquals(0, going.frameAlpha());
        assertTrue(going.emblemScale() < .9f && going.emblemAlpha() > .9f, "the emblem shrinks first: " + going.emblemScale());
        assertEquals(1, going.ringAlpha());
        assertEquals(1, going.pipAlpha());
        var late = at(SKIN, 1, exit + 13, seconds);
        assertTrue(late.emblemScale() < .65f && late.emblemShade() < .5f && late.emblemAlpha() < .5f, "then darkens and fades");
        assertTrue(late.ringAlpha() < .2f, "the ring goes");
        assertEquals(1, late.pipAlpha(), "the pips go last");
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
        assertTrue(settled.shadowAlpha() > .4f, "the dark backdrop behind the banner");
        assertEquals(1, KillBannerPlayer.layers(SKIN, 1, 45 / 60.0, 2, true).labelAlpha(), "HEADSHOT for a head kill");
    }

    @Test
    void bannerSwapTurnsThePreviousKillsArtIntoThisOne() {
        KillBannerStyle swap = KillBannerStyle.CHAMPIONS2024;
        assertEquals(0, at(swap, 3, 5, 2).tier(), "the two-kill art first");
        assertEquals(1, at(swap, 3, 14, 2).tier(), "then the three-kill art");
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
