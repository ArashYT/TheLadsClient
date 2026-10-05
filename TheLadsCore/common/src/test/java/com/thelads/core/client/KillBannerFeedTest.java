package com.thelads.core.client;

import com.thelads.core.client.killbanner.KillBannerFeed;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KillBannerFeedTest {
    private static final long WAIT = 20_000;

    /** What the player drew before the feed: the strip's frame, recoloured on the spot, from a strip of its own. */
    private static byte[] expected(KillBannerStyle style, int kills, int variant, int frame) throws Exception {
        try (InputStream in = KillBannerFeedTest.class.getResourceAsStream(style.asset("k" + kills + ".lkb"))) {
            byte[] rgba = KillBannerStrip.read(in).frame(frame).clone();
            style.recolor(rgba, variant);
            return rgba;
        }
    }

    @Test
    void feedsTheRecolouredFramesForwardJumpsAndBackwards() throws Exception {
        for (KillBannerStyle style : new KillBannerStyle[] {KillBannerStyle.REAVER, KillBannerStyle.ROGUE}) {
            KillBannerStrip strip = style.strip(1);
            KillBannerFeed feed = new KillBannerFeed(style);
            feed.target(strip, 1);
            // In order, as a banner plays (the worker is ahead of every frame after the first few).
            for (int i = 0; i < 14; i++) assertArrayEquals(expected(style, 1, 1, i), feed.get(i, WAIT), style + " frame " + i);
            // A jump past what was decoded, then a few frames back (still held), then well back (the stream starts over).
            assertArrayEquals(expected(style, 1, 1, 40), feed.get(40, WAIT), style + " jump to 40");
            assertTrue(feed.ready(38), "a frame just behind is still held");
            assertArrayEquals(expected(style, 1, 1, 38), feed.get(38, WAIT), style + " back to 38");
            assertArrayEquals(expected(style, 1, 1, 3), feed.get(3, WAIT), style + " back to 3");
            assertArrayEquals(expected(style, 1, 1, strip.frames - 1), feed.get(strip.frames + 50, WAIT), style + " past the end shows the last frame");
            // Another variant of the same strip, and another strip.
            feed.target(strip, 2);
            assertArrayEquals(expected(style, 1, 2, 5), feed.get(5, WAIT), style + " variant 2");
            feed.target(style.strip(2), 0);
            assertArrayEquals(expected(style, 2, 0, 0), feed.get(0, WAIT), style + " two kills, frame 0");
            feed.release();
            assertNull(feed.get(0, 0), "a released feed has nothing");
        }
    }

    @Test
    void aFrameNotReadyYetIsNullNotAWait() {
        KillBannerFeed feed = new KillBannerFeed(KillBannerStyle.REAVER);
        KillBannerStrip strip = KillBannerStyle.REAVER.strip(5);
        feed.target(strip, 0);
        assertNull(feed.get(strip.frames - 1, 0), "200 frames away and no time to wait");
        assertNotNull(feed.get(strip.frames - 1, WAIT), "the worker gets there");
        feed.release();
    }

    @Test
    void releasingStripsEndsTheirDecodersAndTheyReadAgain() {
        KillBannerStyle style = KillBannerStyle.ROGUE;
        KillBannerStrip strip = style.strip(1);
        byte[] before = strip.frame(10).clone();
        style.release();
        assertNull(style.loadedStrip(1), "released");
        assertArrayEquals(before, strip.frame(10).clone(), "a released strip still decodes (a new inflater)");
        assertNotSame(strip, style.strip(1), "read again from the resource");
    }
}
