package com.thelads.core.client;

import com.thelads.core.modules.KillBannerModule;

/** A short cosmetic sequence, not a claimed server round or lifetime kill streak. */
public final class KillBannerTimeline {
    private boolean showing;
    private long started;
    private int sequence;
    private int delta;
    private boolean preview;
    private boolean headshot;
    private KillBannerModule.Pick pick;

    public void trigger(int kills, long now, boolean isPreview) {
        trigger(kills, now, isPreview, false);
    }

    /** {@code head}: the killing blow the client saw landed on the head. */
    public void trigger(int kills, long now, boolean isPreview, boolean head) {
        trigger(kills, now, isPreview, head, null);
    }

    /** Each kill restarts the animation (the newest kill's banner); {@code shown} is what it shows, null for the module's choice. */
    public void trigger(int kills, long now, boolean isPreview, boolean head, KillBannerModule.Pick shown) {
        if (kills <= 0) return;
        sequence = showing && now - started >= 0 && now - started <= 8_000_000_000L && !preview && !isPreview
            ? (int) Math.min(5L, (long) sequence + kills) : Math.min(5, kills);
        delta = kills;
        started = now;
        showing = true;
        preview = isPreview;
        headshot = head;
        pick = shown;
    }
    public double age(long now) { return showing && now >= started ? (now - started) / 1_000_000_000d : -1; }
    public int sequence() { return sequence; }
    public int delta() { return delta; }
    public boolean preview() { return preview; }
    public boolean headshot() { return headshot; }
    /** What the current banner shows, or null to follow the module's current choice. */
    public KillBannerModule.Pick pick() { return pick; }
    public void clear() { showing = false; sequence = delta = 0; preview = headshot = false; pick = null; }

    public static double opacity(double age, double duration) {
        if (age < 0 || !Double.isFinite(age) || !Double.isFinite(duration) || age >= duration) return 0;
        return Math.clamp(Math.min(age / .12, (duration - age) / .35), 0, 1);
    }
}
