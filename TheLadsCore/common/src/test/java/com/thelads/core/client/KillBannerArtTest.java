package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillBannerArtTest {
    @Test
    void everyStripDecodesFromItsFirstFrameToItsLast() {
        for (KillBannerStyle style : KillBannerStyle.values()) {
            if (!style.isAnimated()) continue;
            for (int kills = 1; kills <= 5; kills++) {
                KillBannerStrip strip = style.strip(kills);
                byte[] settled = strip.frame(strip.introEnd).clone();
                assertTrue(opaque(settled) > 2000, style + " k" + kills + " settles on a drawn banner");
                byte[] last = strip.frame(strip.frames - 1);
                assertEquals(strip.width * strip.height * 4, last.length);
                // Going back restarts the stream and lands on the same pixels.
                assertArrayEquals(settled, strip.frame(strip.introEnd), style + " k" + kills + " rewinds exactly");
                if (strip.exitFrames == 0) assertTrue(strip.exitIcon.length > 0 && strip.exitRest.length > 0, style + " drawn way out");
            }
        }
        assertSame(KillBannerStyle.REAVER.strip(9), KillBannerStyle.REAVER.strip(5), "more than five kills show as five");
    }

    @Test
    void allBannersHaveValidAssetsAndSounds() {
        for (KillBannerStyle style : KillBannerStyle.values()) {
            assertNotNull(style.id);
            assertNotNull(style.displayName);
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                for (int k = 1; k <= 6; k++) {
                    assertNotNull(KillBannerArtTest.class.getResourceAsStream(style.swapAsset(k)),
                        style.id + " k" + k + ".png missing");
                }
            } else if (!style.isAnimated()) {
                if (style.hasFrame) {
                    assertNotNull(KillBannerArtTest.class.getResourceAsStream(style.frameAsset()),
                        style.id + " frame.png missing");
                }
                if (style.hasRing) {
                    assertNotNull(KillBannerArtTest.class.getResourceAsStream(style.ringAsset()),
                        style.id + " ring.png missing");
                }
                if (style.hasEmblem) {
                    assertNotNull(KillBannerArtTest.class.getResourceAsStream(style.emblemAsset(0)),
                        style.id + " emblem.png missing");
                }
                if (style.hasPip) {
                    assertNotNull(KillBannerArtTest.class.getResourceAsStream(style.pipAsset(0)),
                        style.id + " pip.png missing");
                }
            }
            for (int s = 1; s <= style.soundCount; s++) {
                String path = "/assets/theladscore/sounds/killbanner/" + style.id + "-kill-" + s + ".ogg";
                assertNotNull(KillBannerArtTest.class.getResourceAsStream(path), path + " missing");
            }
        }
    }

    @Test
    void variantsRecolourOnlyTheAccent() {
        byte[] pixels = {(byte) 117, 52, (byte) 182, (byte) 255,   // Reaver purple
            (byte) 240, (byte) 240, (byte) 240, (byte) 255}; // the white emblem
        KillBannerStyle.REAVER.recolor(pixels, 1);
        assertTrue((pixels[0] & 255) > 2 * (pixels[1] & 255) && (pixels[0] & 255) > 2 * (pixels[2] & 255), "Red turns the purple red");
        assertEquals(240, pixels[4] & 255, "white stays white");
        byte[] rogue = {(byte) 167, 30, 3, (byte) 255};
        KillBannerStyle.ROGUE.recolor(rogue, 1);
        assertTrue((rogue[1] & 255) > (rogue[0] & 255), "Green turns Rogue's red green");
    }

    @Test
    void theMarkLandsAndStrobesAsInTheGame() {
        KillBannerStyle style = KillBannerStyle.ROGUE;
        KillBannerStrip strip = style.strip(1);
        int m = KillBannerStyle.MARK_FRAME;
        assertEquals(0, KillBannerPlayer.at(style, strip, (m - 1) / 60.0, 2, false).markSize());
        var landing = KillBannerPlayer.at(style, strip, (m + .5) / 60.0, 2, false);
        assertTrue(landing.markSize() > style.markSize * 2, "the mark arrives large");
        assertEquals(0xFFFFFFFF, landing.markColor(), "and white");
        var settled = KillBannerPlayer.at(style, strip, 1.0, 2, false);
        assertEquals(style.markSize, settled.markSize(), .05f);
        assertEquals(KillBannerPlayer.MARK_RED, settled.markColor(), "settled red");
        assertEquals(0, settled.strobe());
        // The strobe peaks every 6 frames (100 ms).
        assertEquals(1f, KillBannerPlayer.at(style, strip, (m + 5.5) / 60.0, 2, false).strobe());
        assertEquals(1f, KillBannerPlayer.at(style, strip, (m + 11.5) / 60.0, 2, false).strobe());
    }

    @Test
    void rogueAceIconDropsInWithItsRing() {
        KillBannerStyle style = KillBannerStyle.ROGUE;
        KillBannerStrip ace = style.strip(5);
        // The ace's ring drops in from 22 cell pixels up (frames 5-23); its icon, and what is drawn on it, come with it.
        assertEquals(-22, KillBannerPlayer.at(style, ace, 11.5 / 60, 4, false).iconY());
        assertTrue(KillBannerPlayer.at(style, ace, 18.5 / 60, 4, false).iconY() > -22);
        assertEquals(0, KillBannerPlayer.at(style, ace, 1.0, 4, false).iconY(), "settled in its place");
        // The icon itself moved in the frames: its settled place is clear while it is up with the ring.
        byte[] up = ace.frame(10).clone(), settled = ace.frame(ace.introEnd).clone();
        int row = 139, x = 158; // the chin's settled place
        assertTrue((settled[(row * ace.width + x) * 4 + 3] & 255) > 200 && (up[(row * ace.width + x) * 4 + 3] & 255) < 40);
        for (int kills = 1; kills <= 4; kills++) {
            KillBannerStrip strip = style.strip(kills);
            for (int f = 0; f < strip.frames; f++) assertEquals(0, strip.iconY(f), "k" + kills + " keeps its icon in place");
        }
    }

    @Test
    void holdsForTheDurationThenLeaves() {
        KillBannerStyle style = KillBannerStyle.REAVER;
        KillBannerStrip strip = style.strip(1);
        double minimum = KillBannerPlayer.minimumSeconds(strip);
        var held = KillBannerPlayer.at(style, strip, (strip.introEnd + 30) / 60.0, minimum + 1, true);
        assertEquals(strip.introEnd, held.stripFrame(), "the settled frame holds");
        assertEquals(1f, held.labelAlpha(), "HEADSHOT shows for a head kill");
        var leaving = KillBannerPlayer.at(style, strip, minimum + 1 - .1, minimum + 1, false);
        assertTrue(leaving.drawnExit() && leaving.iconAlpha() == 0 && leaving.restAlpha() < 1, "Reaver's drawn way out");
        assertNull(KillBannerPlayer.at(style, strip, minimum + 1, minimum + 1, false), "then it is gone");
        assertNull(KillBannerPlayer.at(KillBannerStyle.ROGUE, KillBannerStyle.ROGUE.strip(1), 9, 2, false));
        var rogueOut = KillBannerPlayer.at(KillBannerStyle.ROGUE, KillBannerStyle.ROGUE.strip(1), 2 - .05, 2, false);
        assertTrue(rogueOut.stripFrame() > KillBannerStyle.ROGUE.strip(1).introEnd, "Rogue plays its own way out");
    }

    private static int opaque(byte[] rgba) {
        int n = 0;
        for (int i = 3; i < rgba.length; i += 4) if ((rgba[i] & 255) > 128) n++;
        return n;
    }
}
