package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The authoritative server kill counter (ServerKillTracker) and the cosmetic banner sequence (KillBannerTimeline). */
class NativeKillBannerTest {
    @Test void killCounterReportsOnlyNewServerKills() {
        var counter = new ServerKillTracker();
        assertEquals(0, counter.observe(800), "login history does not fabricate kills");
        assertEquals(0, counter.observe(800), "duplicate response does not replay a kill");
        assertEquals(1, counter.observe(801), "one newly reported player kill");
        assertEquals(3, counter.observe(804), "batched server updates retain exact delta");
        assertEquals(0, counter.observe(5), "server statistic reset is not a kill");
        assertEquals(1, counter.observe(6), "tracking recovers after reset");
        assertEquals(0, counter.observe(-1), "invalid negative statistic ignored");
        assertEquals(0, counter.observe(6), "invalid statistic did not erase baseline");
        counter.reset();
        assertEquals(0, counter.observe(15000), "new server history establishes independent baseline");
        counter.reset();
        assertEquals(0, counter.observe(0));
        assertEquals(Integer.MAX_VALUE, counter.observe(Integer.MAX_VALUE), "large valid delta does not overflow");
    }
    @Test void bannerSequenceIsCosmeticAndBounded() {
        var banner = new KillBannerTimeline();
        long now = 1_000_000_000L;
        assertTrue(banner.age(now) < 0, "nothing shown before an event");
        banner.trigger(1, now, false);
        assertTrue(banner.sequence() == 1 && banner.delta() == 1 && !banner.preview(), "single kill starts sequence");
        banner.trigger(2, now + 1_000_000_000L, false);
        assertTrue(banner.sequence() == 3 && banner.delta() == 2, "nearby kills advance cosmetic sequence");
        banner.trigger(Integer.MAX_VALUE, now + 2_000_000_000L, false);
        assertEquals(5, banner.sequence(), "sound rank capped without integer overflow");
        banner.trigger(1, now + 11_000_000_001L, false);
        assertEquals(1, banner.sequence(), "expired sequence restarts");
        banner.trigger(3, now + 12_000_000_000L, true);
        assertTrue(banner.preview() && banner.sequence() == 3, "explicit preview is marked");
        banner.trigger(1, now + 13_000_000_000L, false);
        assertTrue(!banner.preview() && banner.sequence() == 1, "preview never inflates real sequence");
        banner.clear();
        assertTrue(banner.age(now) < 0 && banner.sequence() == 0, "disable/death clears stale banner");
    }
    @Test void bannerOpacityFadesInAndOut() {
        assertEquals(0, KillBannerTimeline.opacity(0, 2.5), "entrance starts transparent");
        assertEquals(1, KillBannerTimeline.opacity(.2, 2.5), "banner reaches full opacity");
        double exit = KillBannerTimeline.opacity(2.4, 2.5);
        assertTrue(exit > 0 && exit < 1, "smooth exit before duration");
        assertEquals(0, KillBannerTimeline.opacity(2.5, 2.5), "expires at selected duration");
        assertEquals(0, KillBannerTimeline.opacity(Double.NaN, 2.5), "invalid animation time is hidden");
    }
}
