package com.thelads.core.client;

import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayDeque;

/**
 * The kill streak and the banner on screen. Every kill counts toward the streak the moment it is seen; its banner queues, so
 * kills that land together (the same tick, or while a banner is still opening) still show 1, 2, 3... in turn. As in Valorant,
 * the next kill's banner replaces the one playing straight away, but no sooner than {@link #GAP} after that one started.
 */
public final class KillBannerTimeline {
    /** Kills that land together still show one banner each, this far apart: long enough to see each count land. */
    public static final long GAP = 250_000_000L;
    private record Queued(int sequence, boolean head, KillBannerModule.Pick pick) {}
    private final ArrayDeque<Queued> queue = new ArrayDeque<>();
    private boolean showing;
    private long started, lastKill;
    private int sequence, streak;
    private boolean preview;
    private boolean headshot;
    private KillBannerModule.Pick pick;
    private double frozen = Double.NaN;

    /**
     * A kill: it counts toward the streak now (a new streak when more than {@code window} ns passed since the last kill;
     * negative: never), and its banner joins the queue ({@link #next}).
     */
    public void kill(long now, long window, boolean head, KillBannerModule.Pick shown) {
        streak = streak > 0 && (window < 0 || now - lastKill <= window) ? streak + 1 : 1;
        lastKill = now;
        // A flood of kills: the five banners already waiting show it (the streak still counts every kill).
        if (queue.size() < 5) queue.addLast(new Queued(Math.min(5, streak), head, shown));
    }

    /** Starts the next queued banner once the one playing has had {@link #GAP}; true when one started (play its sound). */
    public boolean next(long now) {
        if (queue.isEmpty() || showing && !preview && now - started < GAP) return false;
        Queued next = queue.removeFirst();
        show(next.sequence(), now, false, next.head(), next.pick());
        return true;
    }

    public void trigger(int kills, long now, boolean isPreview) {
        trigger(kills, now, isPreview, false);
    }

    /** {@code head}: the killing blow the client saw landed on the head. */
    public void trigger(int kills, long now, boolean isPreview, boolean head) {
        trigger(kills, now, isPreview, head, null);
    }

    /** A preview or QA banner for {@code kills} kills, shown now; the streak is left alone. {@code shown} null: the module's choice. */
    public void trigger(int kills, long now, boolean isPreview, boolean head, KillBannerModule.Pick shown) {
        if (kills > 0) show(Math.min(5, kills), now, isPreview, head, shown);
    }

    private void show(int kills, long now, boolean isPreview, boolean head, KillBannerModule.Pick shown) {
        sequence = kills;
        started = now;
        showing = true;
        preview = isPreview;
        headshot = head;
        pick = shown;
        frozen = Double.NaN;
    }

    public double age(long now) {
        if (showing && !Double.isNaN(frozen)) return frozen;
        return showing && now >= started ? (now - started) / 1_000_000_000d : -1;
    }
    /** QA: the banner stays {@code age} seconds after its kill until the next kill or clear (NaN plays it again). */
    public void freeze(double age) { frozen = age; }
    /** The banner on screen: 1 to 5 kills. */
    public int sequence() { return sequence; }
    /** Kills in the current streak (past 5 too); 0 before the first. */
    public int streak() { return streak; }
    /** Banners waiting for their turn. */
    public int queued() { return queue.size(); }
    public boolean preview() { return preview; }
    public boolean headshot() { return headshot; }
    /** What the current banner shows, or null to follow the module's current choice. */
    public KillBannerModule.Pick pick() { return pick; }
    /** The local player died: the next kill starts a new streak (the banners already earned still play). */
    public void endStreak() { streak = 0; }
    /** Death, a new world or server, or the module off: no banner, no streak, nothing queued. */
    public void clear() {
        showing = false; sequence = streak = 0; preview = headshot = false; pick = null; frozen = Double.NaN;
        queue.clear();
    }

    public static double opacity(double age, double duration) {
        if (age < 0 || !Double.isFinite(age) || !Double.isFinite(duration) || age >= duration) return 0;
        return Math.clamp(Math.min(age / .12, (duration - age) / .35), 0, 1);
    }
}
