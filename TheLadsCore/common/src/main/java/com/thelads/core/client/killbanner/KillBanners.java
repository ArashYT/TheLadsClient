package com.thelads.core.client.killbanner;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.hud.HudFrameCap;
import com.thelads.core.modules.KillBannerModule;

/** The Kill Banner state every version shares: kill detection, the banner on screen and the sound each kill plays. */
public final class KillBanners {
    public static final KillDetector DETECTOR = new KillDetector();
    public static final KillBannerTimeline TIMELINE = new KillBannerTimeline();

    private KillBanners() {}

    public static void reset() {
        DETECTOR.reset();
        TIMELINE.clear();
    }

    /**
     * A kill, the moment it is seen: it counts toward the streak and its banner queues (KillBannerTimeline). Returns the sound
     * of a banner that started now, as {@link #poll} does.
     */
    public static String fire(KillBannerModule module, KillDetector.Kill kill, long now) {
        if (kill == null || !module.counts(kill.kind())) return null;
        boolean headshot = kill.headshot() || module.headshots.getIndex() == KillBannerModule.HEADSHOTS_EVERY_KILL;
        TIMELINE.kill(now, module.streakWindow(), headshot, module.next());
        return poll(module, now);
    }

    /**
     * Each client tick (and right after a kill): starts the next queued banner when its turn has come. Returns its sound (each
     * banner plays its own): "theladscore:&lt;skin&gt;_kill_&lt;n&gt;", "" for the plain chime, or null when nothing plays.
     */
    public static String poll(KillBannerModule module, long now) {
        if (!TIMELINE.next(now)) return null;
        // A capped HUD rebuilds on the next frame instead of its next due one, so the banner is not late.
        HudFrameCap.reset();
        return sound(module, TIMELINE.pick());
    }

    /** A preview or QA banner with a set look. */
    public static String show(KillBannerModule module, int kills, boolean preview, boolean headshot, KillBannerModule.Pick pick, long now) {
        TIMELINE.trigger(kills, now, preview, headshot, pick);
        HudFrameCap.reset();
        return sound(module, pick);
    }

    private static String sound(KillBannerModule module, KillBannerModule.Pick pick) {
        if (!module.sound.get() || module.volume.getValue() <= 0) return null;
        if (pick.soundStyle() == null) return "";
        int seq = Math.max(1, Math.min(pick.soundStyle().soundCount, TIMELINE.sequence()));
        return "theladscore:" + pick.soundStyle().soundId(pick.soundStyle() == pick.style() ? pick.variant() : 0) + "_kill_" + seq;
    }

    /** What the banner on screen shows: its kill's pick, else the module's current choice. */
    public static KillBannerModule.Pick shown(KillBannerModule module) {
        return TIMELINE.pick() != null ? TIMELINE.pick() : module.chosen();
    }
}
