package com.thelads.core.v26_2.feature;

/** A short cosmetic sequence, not a claimed server round or lifetime kill streak. */
public final class KillBannerTimeline {
    private boolean showing;
    private long started;
    private int sequence;
    private int delta;
    private boolean preview;

    public void trigger(int kills, long now, boolean isPreview) {
        if (kills <= 0) return;
        sequence = showing && now - started >= 0 && now - started <= 8_000_000_000L && !preview && !isPreview
            ? (int) Math.min(5L, (long) sequence + kills) : Math.min(5, kills);
        delta = kills;
        started = now;
        showing = true;
        preview = isPreview;
    }
    public double age(long now) { return showing && now >= started ? (now - started) / 1_000_000_000d : -1; }
    public int sequence() { return sequence; }
    public int delta() { return delta; }
    public boolean preview() { return preview; }
    public void clear() { showing = false; sequence = delta = 0; preview = false; }

    public static double opacity(double age, double duration) {
        if (age < 0 || !Double.isFinite(age) || !Double.isFinite(duration) || age >= duration) return 0;
        return Math.clamp(Math.min(age / .12, (duration - age) / .35), 0, 1);
    }
}
