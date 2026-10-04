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
     * Shows the banner for a kill the moment it is seen. Returns the sound to play for this kill (every kill plays its own,
     * so quick kills stack while the animation restarts on the newest): "theladscore:&lt;skin&gt;_kill_&lt;n&gt;", "" for the
     * plain chime, or null when nothing plays (the kind is off, or sound is off).
     */
    public static String fire(KillBannerModule module, KillDetector.Kill kill, long now) {
        if (kill == null || !module.counts(kill.kind())) return null;
        KillBannerModule.Pick pick = module.next();
        TIMELINE.trigger(1, now, false, kill.headshot(), pick);
        // A capped HUD rebuilds on the next frame instead of its next due one, so the banner is not late.
        HudFrameCap.reset();
        return sound(module, pick);
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
        return "theladscore:" + pick.soundStyle().id + "_kill_" + seq;
    }

    /** What the banner on screen shows: its kill's pick, else the module's current choice. */
    public static KillBannerModule.Pick shown(KillBannerModule module) {
        return TIMELINE.pick() != null ? TIMELINE.pick() : module.chosen();
    }
}
