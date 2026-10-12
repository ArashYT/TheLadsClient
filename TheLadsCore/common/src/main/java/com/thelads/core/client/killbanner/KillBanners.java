package com.thelads.core.client.killbanner;

import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.ActionBar;
import com.thelads.core.client.hud.HudFrameCap;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.modules.KillBannerModule;

/** The Kill Banner state every version shares: kill detection, the banner on screen and the sound each kill plays. */
public final class KillBanners {
    public static final KillDetector DETECTOR = new KillDetector();
    public static final KillBannerTimeline TIMELINE = new KillBannerTimeline();
    /** GUI pixels between a raised banner's bottom and the action bar under it. */
    static final int ACTION_BAR_GAP = 2;

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
     * banner plays its own): "theladscore:&lt;skin&gt;_kill_&lt;n&gt;", "" for the plain chime, or null when nothing (more) plays:
     * the player's own uploaded sound has already started.
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
        if (pick.ownSounds()) {
            // Your sound plays here (CustomSounds, any version); an empty slot is the chime.
            String file = module.killSound(TIMELINE.sequence());
            if (file.isEmpty()) return "";
            CustomSounds.play(file, module.volume.getValue());
            return null;
        }
        if (pick.soundStyle() == null) return "";
        int seq = Math.max(1, Math.min(pick.soundStyle().soundCount, TIMELINE.sequence()));
        return "theladscore:" + pick.soundStyle().soundId(pick.soundStyle() == pick.style() ? pick.variant() : 0) + "_kill_" + seq;
    }

    /**
     * GUI pixels the banner on screen is raised: one that starts while an action bar shows over it plays just above that action bar
     * for its whole animation, even once the action bar fades; the next banner decides again at its own first frame. {@code g} is
     * in GUI units; the banner is where the adapters draw a skin ({@code size} 1 = 100%): its ring centre at 79.4% of the screen
     * height, down to its HEADSHOT label's bottom and as wide as its frame.
     */
    public static float lift(LadsGraphics g, KillBannerStyle style, float size) {
        float lift = TIMELINE.lift();
        if (!Float.isNaN(lift)) return lift;
        lift = 0;
        int[] bar = ActionBar.spot(g);
        if (bar != null) {
            KillBannerStyle s = style != null ? style : KillBannerStyle.DEFAULT;
            float k = g.getScaledHeight() * KillBannerStyle.SCREEN_SCALE / 1080f * size * KillBannerStyle.ART_SCALE;
            float cx = g.getScaledWidth() / 2f, cy = g.getScaledHeight() * .794f, half = s.ring * 1.6f * k;
            float bottom = cy + (s.labelY + s.ring * 1.45f * 27 / 105f) * k;
            if (bar[0] < cx + half && bar[2] > cx - half && bar[1] < bottom && bar[3] > cy - half)
                lift = Math.min(bottom - bar[1] + ACTION_BAR_GAP, cy - half); // its top stays on screen
        }
        TIMELINE.lift(lift);
        return lift;
    }

    /** What the banner on screen shows: its kill's pick, else the module's current choice. */
    public static KillBannerModule.Pick shown(KillBannerModule module) {
        return TIMELINE.pick() != null ? TIMELINE.pick() : module.chosen();
    }
}
