package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBannerTemplate;
import org.junit.jupiter.api.Test;

/** The HEADSHOT banner's box colour: measured from the skin's preview where it showed one, guessed from the accent otherwise. */
class KillBannerHeadshotTest {
    @Test void aGuessedBoxIsADarkerDullerAccent() {
        assertEquals(0x804242, KillBannerStyle.guessBox(0xFF0000), "red at half saturation and brightness");
        assertEquals(0x42805A, KillBannerStyle.guessBox(0x00FF63), "a green");
        assertEquals(0x4A4A4A, KillBannerStyle.guessBox(0xFFFFFF), "white has no hue: dark grey");
        assertEquals(0x4A4A4A, KillBannerStyle.guessBox(0x303030));
    }

    @Test void everySkinHasABoxColour() {
        for (KillBannerStyle style : KillBannerStyle.values()) {
            int box = style.headshotBox(0);
            assertTrue(box >= 0 && box <= 0xFFFFFF, style.id);
            int max = Math.max((box >> 16) & 255, Math.max((box >> 8) & 255, box & 255));
            assertTrue(max >= 0x40 && max <= 0xC0, style.id + ": a box dark enough for white text, " + Integer.toHexString(box));
        }
    }

    @Test void animatedSkinsGuessFromTheirAccent() {
        assertEquals(-1, KillBannerTemplate.headshotBox(KillBannerStyle.REAVER), "Reaver's strips carry no measured box");
        assertEquals(KillBannerStyle.guessBox(KillBannerStyle.REAVER.accent(0)), KillBannerStyle.REAVER.headshotBox(0));
    }
}
