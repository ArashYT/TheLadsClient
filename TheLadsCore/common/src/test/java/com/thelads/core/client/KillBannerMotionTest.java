package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBannerTemplate;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The still skins' animation (KillBannerPlayer.layers): the game's own motion (KillBannerTemplate) on their layers. */
class KillBannerMotionTest {
    private static final KillBannerStyle SKIN = KillBannerStyle.ONI; // pip radius 86 art px: the game's 172 / 2
    /** The game's events at 60 fps: the slices light up (and the mark lands) at 0.15 s, the wheel spins at 0.75 s. */
    private static final int LIT = 9, SPIN = 45;

    private static KillBannerPlayer.Layers at(KillBannerStyle style, int kills, double frame, double seconds) {
        return KillBannerPlayer.layers(style, kills, frame / 60, seconds, false);
    }

    @Test
    void theTemplateIsTheGamesAnimation() {
        // IntroAnimation at speed 1 (2.1 s: the emblem goes at 1.8 s, the rest from 1.9 s), the ace at the game's 0.3.
        for (int kills = 1; kills <= 5; kills++) {
            KillBannerTemplate t = KillBannerTemplate.of(kills);
            assertEquals(kills == 5 ? 360 : 107, t.introEnd, "k" + kills + " holds until the emblem's fade-out event");
            assertEquals(kills == 5 ? 62 : 19, t.exit, "k" + kills + " leaves as the game's holder fades");
            assertEquals(kills == 5 ? 30 : LIT, t.mark, "the mark lands with the slices");
            assertEquals(t.introEnd + 1 + t.exit, t.iconAlpha.length);
            assertEquals(0, t.iconAlpha[0], "nothing in the first frame");
            assertEquals(1, t.iconAlpha[2], "the emblem dissolves in over 0.02 s");
            assertEquals(1, t.iconAlpha[t.introEnd]);
            assertEquals(1, t.ringAlpha[t.introEnd]);
            assertEquals(1, t.frameAlpha[t.introEnd]);
            assertEquals(1, t.pipAlpha[t.introEnd]);
            assertEquals(0, t.iconAlpha[t.introEnd + t.exit], "the icon is gone by the last frame");
            assertEquals(0, t.ringAlpha[t.introEnd + t.exit], .01f, "and so is the wheel");
            assertSame(t, KillBannerTemplate.of(SKIN, kills), "every skin plays the game's motion");
        }
        assertEquals(0, KillBannerTemplate.of(1).sprayCount, "one kill has no FX tier");
        for (int kills = 2; kills <= 5; kills++) assertEquals(KillBannerTemplate.of(kills).mark, KillBannerTemplate.of(kills).sprayStart, "the FX fire with the slices");
        assertEquals(KillBannerTemplate.of(5), KillBannerTemplate.of(9), "past five kills: the ace");
        assertSame(KillBannerTemplate.of(3), KillBannerTemplate.of(KillBannerStyle.REAVER, 3), "the strips keep the shared timing");
    }

    @Test
    void theEmblemDropsInAsTheWheelGrowsIntoPlaceAndTheFrameDissolvesIn() {
        assertEquals(0, at(SKIN, 1, 0, 2).emblemAlpha(), "nothing yet");
        var first = at(SKIN, 1, 1, 2);
        assertTrue(first.emblemAlpha() > .8f, "the emblem is there in a frame: " + first.emblemAlpha());
        assertEquals(-30, first.emblemY(), .01f, "30 px above its place (the holder slides down from 0.05 s to 0.1 s)");
        assertEquals(0, at(SKIN, 1, 6, 2).emblemY(), .01f, "in place by frame 6");
        assertEquals(1, at(SKIN, 1, 6, 2).emblemScale(), .01f);
        assertEquals(0, first.ringAlpha(), "the wheel is still clear");
        assertEquals(1.1f, first.ringScale(), .001f, "and a tenth large");
        var wheel = at(SKIN, 1, 9, 2);
        assertEquals(1, wheel.ringScale(), .001f, "it shrinks into place by 0.15 s");
        assertTrue(wheel.ringAlpha() > .25f && wheel.ringAlpha() < .35f, "while its top-down dissolve runs 0.5 s: " + wheel.ringAlpha());
        assertEquals(1, at(SKIN, 1, 30, 2).ringAlpha(), .001f);
        assertEquals(.5f, at(SKIN, 1, 9, 2).frameAlpha(), .01f, "the frame dissolves in over 0.3 s");
        assertEquals(1, at(SKIN, 1, 18, 2).frameAlpha(), .001f);
        assertEquals(1, at(SKIN, 1, 18, 2).frameScale(), .001f);
    }

    @Test
    void everyPipLightsUpOutsideThenSlidesIntoPlace() {
        var dim = at(SKIN, 1, 6, 2);
        assertEquals(.3f, dim.pipUp(), .001f, "the Up texture at 0.3 before the slices light");
        assertEquals(0, dim.pipFlare(), "no hover yet");
        assertEquals(1, dim.pipRadius(), .001f);
        var lit = at(SKIN, 1, LIT, 2);
        assertEquals(1, lit.pipUp(), .001f);
        assertEquals(1, lit.pipAlpha(), .001f);
        assertEquals(1 + 15f / SKIN.ring, lit.pipRadius(), .001f, "15 px out");
        assertEquals(1.2f, lit.pipScale(), .001f, "and a fifth large");
        assertEquals(1, at(SKIN, 1, LIT + 9, 2).pipFlare(), .001f, "the hover is full after 0.15 s");
        assertEquals(1, at(SKIN, 1, LIT + 18, 2).pipFlare(), .001f, "held to 0.3 s");
        assertEquals(1 + 15f / SKIN.ring, at(SKIN, 1, LIT + 18, 2).pipRadius(), .001f, "still out");
        var settled = at(SKIN, 1, LIT + 45, 2);
        assertEquals(1, settled.pipRadius(), .001f, "in place by 0.75 s");
        assertEquals(1, settled.pipScale(), .001f);
        assertEquals(.6f, settled.pipFlare(), .001f, "the hover settles at 0.6");
        assertEquals(3, at(SKIN, 3, 60, 2).pipDegrees().length, "a pip a kill, every one lit");
        assertEquals(0, at(SKIN, 1, 100, 2).pipSpin(), .001f, "one kill does not spin");
    }

    @Test
    void theWheelSpinsAsTheGamesFInterpTo() {
        // From the 0.75 s event, exponentially (speed 8 a second) to -360 / kills degrees; the ace two turns the other way at 5.
        assertEquals(0, at(SKIN, 2, SPIN, 5).pipSpin(), .001f);
        var turning = at(SKIN, 2, SPIN + 15, 5);
        assertTrue(turning.pipSpin() > 100 && turning.pipSpin() < 160, "two kills: half a turn, most of it in the first quarter second: " + turning.pipSpin());
        assertEquals(180, at(SKIN, 2, 98, 5).pipSpin(), .001f, "snapped to its goal");
        assertEquals(120, at(SKIN, 3, 100, 5).pipSpin(), .001f);
        assertEquals(90, at(SKIN, 4, 100, 5).pipSpin(), .001f);
        assertEquals(0, at(SKIN, 5, 150, 8).pipSpin(), .001f, "the ace (at 0.3 speed) spins from 2.5 s");
        assertEquals(-720, at(SKIN, 5, 253, 8).pipSpin(), .001f, "two turns clockwise");
        var pips = at(SKIN, 2, 98, 5).pipDegrees();
        assertEquals(2, pips.length);
        assertEquals(180, Math.abs(pips[0] - pips[1]) % 360, .001f, "two pips stay opposite each other");
    }

    @Test
    void motionIsContinuous() {
        // No jumps between 120 fps frames, so it reads as motion, not a cut (the game's spin peaks at 60 degrees a frame).
        for (int kills = 1; kills <= 5; kills++) {
            var before = at(SKIN, kills, 0, 3);
            for (double f = .5; f < 60 * KillBannerPlayer.stillSeconds(SKIN, kills); f += .5) {
                var now = at(SKIN, kills, f, 3);
                if (now == null) break;
                assertEquals(before.emblemY(), now.emblemY(), 8, "k" + kills + " emblem at " + f);
                assertEquals(before.emblemScale(), now.emblemScale(), f < 5 ? .25 : .08, "k" + kills + " emblem size at " + f); // it pops in 0.6 -> 1
                assertEquals(before.ringAlpha(), now.ringAlpha(), .3, "k" + kills + " ring at " + f);
                assertEquals(before.pipSpin(), now.pipSpin(), 31, "k" + kills + " spin at " + f);
                before = now;
            }
        }
    }

    @Test
    void moreKillsFireTheFx() {
        assertTrue(at(SKIN, 1, 40, 2).spray() < 0, "one kill has no FX tier");
        assertEquals(0, KillBannerPlayer.sprayCount(SKIN, 1));
        for (int kills = 2; kills <= 5; kills++) {
            int start = KillBannerTemplate.of(kills).sprayStart;
            assertTrue(at(SKIN, kills, start - 1, 5).spray() < 0);
            assertEquals(0, at(SKIN, kills, start, 5).spray(), 1e-6, kills + " kills throw a spray from frame " + start);
            assertTrue(KillBannerPlayer.sprayCount(SKIN, kills) >= 8 && KillBannerPlayer.sprayCount(SKIN, kills) <= 36);
        }
        assertEquals(7.05, KillBannerPlayer.stillSeconds(SKIN, 5), .01, "the ace plays at 0.3 speed");
        assertEquals(2.12, KillBannerPlayer.stillSeconds(SKIN, 1), .01);
    }

    @Test
    void holdsForTheDurationThenLeavesLikeTheGame() {
        double seconds = 3;
        int exit = (int) Math.round(seconds * 60) - KillBannerTemplate.of(1).exit;
        var held = at(SKIN, 1, exit - 1, seconds);
        assertEquals(1, held.frameAlpha());
        assertEquals(1, held.emblemShade());
        assertEquals(1, held.emblemScale(), .001f);
        // The emblem shrinks to 0.6 and dissolves over 0.3 s; 0.1 s in, the holder fades and the wheel grows back out.
        var going = at(SKIN, 1, exit + 4, seconds);
        assertTrue(going.emblemScale() < .8f && going.emblemAlpha() > .5f, "the emblem shrinks first: " + going.emblemScale());
        assertEquals(1, going.frameAlpha(), .001f);
        assertEquals(1, going.ringAlpha(), .001f);
        var late = at(SKIN, 1, exit + 14, seconds);
        assertEquals(.6f, late.emblemScale(), .01f);
        assertTrue(late.emblemAlpha() < .3f, "then fades");
        assertTrue(late.ringAlpha() < .6f && late.frameAlpha() < .8f, "the wheel and frame go with the holder");
        assertTrue(late.ringScale() > 1.01f, "the wheel grows out as it goes");
        assertNull(at(SKIN, 1, exit + 19, seconds), "then it is gone");
        assertNotNull(at(SKIN, 1, 60 * KillBannerPlayer.stillSeconds(SKIN, 1) - 1, .5), "a short duration still plays it all");
        var cut = KillBannerPlayer.layers(SKIN, 1, 30 / 60.0, 2, false, 26 / 60.0);
        var kept = KillBannerPlayer.layers(SKIN, 1, 30 / 60.0, 2, false, -1);
        assertTrue(cut.emblemScale() < kept.emblemScale(), "cut short at frame 26 by the next kill: at 30 it is four frames into its way out");
        assertNull(KillBannerPlayer.layers(SKIN, 1, 46 / 60.0, 2, false, 26 / 60.0), "and gone when that is over");
        assertNotNull(KillBannerPlayer.layers(SKIN, 1, 46 / 60.0, 2, false, -1), "not cut: still there");
        assertNull(KillBannerPlayer.layers(SKIN, 1, -1, 2, false));
        assertNull(KillBannerPlayer.layers(SKIN, 1, Double.NaN, 2, false));
    }

    @Test
    void theHeadshotFlickerIsTheGames() {
        int m = LIT;
        assertEquals(0, at(SKIN, 1, m - .5, 2).markSize());
        assertEquals(1, at(SKIN, 1, m - .5, 2).emblemFlick(), .001f);
        var landing = at(SKIN, 1, m, 2);
        assertEquals(SKIN.markSize * 2, landing.markSize(), .01f, "the reticle lands at twice its size");
        assertEquals(1, landing.strobe(), .001f, "the emblem flashes red at once");
        assertEquals(1.155f, landing.emblemFlick(), .001f, "and pulses");
        assertEquals(0xFFFFFFFF, landing.markColor(), "the X is white against the red");
        var between = at(SKIN, 1, m + 3, 2);
        assertEquals(0, between.strobe(), .001f, "white three frames later");
        assertEquals(KillBannerPlayer.MARK_RED, between.markColor(), "the X red on the white emblem");
        assertEquals(1, at(SKIN, 1, m + 6, 2).strobe(), .001f, "four red pulses six frames apart");
        assertEquals(1, at(SKIN, 1, m + 18, 2).strobe(), .001f);
        assertEquals(0, at(SKIN, 1, m + 24, 2).strobe(), .001f, "then it is over");
        var settled = at(SKIN, 1, m + 15, 2);
        assertEquals(SKIN.markSize, settled.markSize(), .05f, "the reticle is down to size in 0.25 s");
        assertEquals(0, settled.markThinAlpha(), .001f);
        assertEquals(1, settled.markAlpha(), .001f);
        assertTrue(at(SKIN, 1, 45, 2).shadowAlpha() > .4f, "the dark backdrop behind the banner");
        assertEquals(1, KillBannerPlayer.layers(SKIN, 1, 45 / 60.0, 2, true).labelAlpha(), "HEADSHOT for a head kill");
        assertTrue(KillBannerPlayer.layers(SKIN, 1, 45 / 60.0, 2, true).headshot());
        assertFalse(at(SKIN, 1, 45, 2).headshot());
        assertEquals(KillBannerTemplate.of(5).mark, 30, "the ace's mark lands at its 0.3 speed");
    }

    @Test
    void bannerSwapTurnsThePreviousKillsArtIntoThisOne() {
        KillBannerStyle swap = KillBannerStyle.CHAMPIONS2024;
        assertEquals(0, at(swap, 3, LIT - 3, 2).tier(), "the two-kill art first");
        assertEquals(1, at(swap, 3, LIT + 3, 2).tier(), "then the three-kill art as the mark lands");
        assertEquals(1, at(swap, 1, 5, 2).tier(), "one kill has nothing before it");
        assertEquals(1, at(SKIN, 3, 5, 2).tier());
    }

    @Test
    void theSprayFliesOutAndFades() {
        float[] p = new float[6];
        int shown = 0;
        for (int i = 0; i < KillBannerPlayer.sprayCount(SKIN, 5); i++) {
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
        assertEquals(1, KillBannerPlayer.previewKills(SKIN, 0));
        assertEquals(0, KillBannerPlayer.previewAge(SKIN, 0), 1e-9);
        double t = 0;
        for (int kills = 1; kills <= 5; kills++) {
            assertEquals(kills, KillBannerPlayer.previewKills(SKIN, t + .1));
            assertEquals(.1, KillBannerPlayer.previewAge(SKIN, t + .1), 1e-6);
            double shown = KillBannerPlayer.previewSeconds(SKIN, kills);
            assertNotNull(KillBannerPlayer.layers(SKIN, kills, shown - .05, shown, false));
            assertNull(KillBannerPlayer.layers(SKIN, kills, shown + .05, shown, false), "a gap before the next");
            t += shown + .4;
        }
        assertEquals(1, KillBannerPlayer.previewKills(SKIN, t + .1), "and round again");
    }

    @Test
    void everySkinHasItsGameColours() throws Exception {
        Properties accents = new Properties();
        try (InputStream in = KillBannerMotionTest.class.getResourceAsStream("/assets/theladscore/killbanner/accent.properties")) {
            accents.load(in);
        }
        for (KillBannerStyle style : KillBannerStyle.values()) {
            String colours = accents.getProperty(style.id);
            assertNotNull(colours, style.id + " has its PrimaryColor");
            int expected = style.type == KillBannerStyle.Type.BANNER_SWAP ? 1 : style.variantNames.length;
            assertEquals(expected, colours.split(",").length, style.id + ": one per variant");
        }
        assertEquals(0x70EF5F, KillBannerStyle.ONI.accent(0), "Oni's green (KillBannerData_Oni)");
        assertEquals(0xFF762F, KillBannerStyle.AEMONDIR.accent(0), "Aemondir is the game's Legion");
        assertEquals(KillBannerStyle.AEMONDIR.accent(3), KillBannerStyle.AEMONDIR.accent(9), "past the last variant: the last");
        assertEquals(0x5C00A5, KillBannerStyle.REAVER.accent(0), "Reaver is Soulstealer");
        assertEquals(0xD30C00, KillBannerStyle.ROGUE.accent(0));
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
