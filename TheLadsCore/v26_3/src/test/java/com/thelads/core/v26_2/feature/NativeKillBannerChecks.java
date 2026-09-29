package com.thelads.core.v26_2.feature;

public final class NativeKillBannerChecks {
    private static int passed;
    public static void main(String[] args) {
        var counter = new ServerKillTracker();
        require(counter.observe(800) == 0, "login history does not fabricate kills");
        require(counter.observe(800) == 0, "duplicate response does not replay a kill");
        require(counter.observe(801) == 1, "one newly reported player kill");
        require(counter.observe(804) == 3, "batched server updates retain exact delta");
        require(counter.observe(5) == 0, "server statistic reset is not a kill");
        require(counter.observe(6) == 1, "tracking recovers after reset");
        require(counter.observe(-1) == 0, "invalid negative statistic ignored");
        require(counter.observe(6) == 0, "invalid statistic did not erase baseline");
        counter.reset();
        require(counter.observe(15000) == 0, "new server history establishes independent baseline");
        counter.reset();
        require(counter.observe(0) == 0 && counter.observe(Integer.MAX_VALUE) == Integer.MAX_VALUE, "large valid delta does not overflow");

        var banner = new KillBannerTimeline();
        long now = 1_000_000_000L;
        require(banner.age(now) < 0, "nothing shown before an event");
        banner.trigger(1, now, false);
        require(banner.sequence() == 1 && banner.delta() == 1 && !banner.preview(), "single kill starts sequence");
        banner.trigger(2, now + 1_000_000_000L, false);
        require(banner.sequence() == 3 && banner.delta() == 2, "nearby kills advance cosmetic sequence");
        banner.trigger(Integer.MAX_VALUE, now + 2_000_000_000L, false);
        require(banner.sequence() == 5, "sound rank capped without integer overflow");
        banner.trigger(1, now + 11_000_000_001L, false);
        require(banner.sequence() == 1, "expired sequence restarts");
        banner.trigger(3, now + 12_000_000_000L, true);
        require(banner.preview() && banner.sequence() == 3, "explicit preview is marked");
        banner.trigger(1, now + 13_000_000_000L, false);
        require(!banner.preview() && banner.sequence() == 1, "preview never inflates real sequence");
        require(KillBannerTimeline.opacity(0, 2.5) == 0, "entrance starts transparent");
        require(KillBannerTimeline.opacity(.2, 2.5) == 1, "banner reaches full opacity");
        require(KillBannerTimeline.opacity(2.4, 2.5) > 0 && KillBannerTimeline.opacity(2.4, 2.5) < 1, "smooth exit before duration");
        require(KillBannerTimeline.opacity(2.5, 2.5) == 0, "expires at selected duration");
        require(KillBannerTimeline.opacity(Double.NaN, 2.5) == 0, "invalid animation time is hidden");
        banner.clear();
        require(banner.age(now) < 0 && banner.sequence() == 0, "disable/death clears stale banner");
        System.out.println("PASS " + passed + " authoritative kill counter and banner checks");
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}
