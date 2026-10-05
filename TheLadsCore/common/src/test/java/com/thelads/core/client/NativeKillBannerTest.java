package com.thelads.core.client;

import com.google.gson.JsonPrimitive;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The kill streak, its banner queue (KillBannerTimeline, KillBanners) and the streak settings. */
class NativeKillBannerTest {
    private static final long S = 1_000_000_000L, GAP = KillBannerTimeline.GAP;
    private static final KillBannerModule.Pick PICK = new KillBannerModule.Pick(null, 0, null);

    /** Shows what the queue lets through from {@code from}, a tick (50 ms) at a time, for {@code seconds}: each banner's kill count. */
    private static List<Integer> play(KillBannerTimeline t, long from, double seconds) {
        List<Integer> shown = new ArrayList<>();
        for (long now = from; now <= from + (long) (seconds * S); now += 50_000_000L) if (t.next(now)) shown.add(t.sequence());
        return shown;
    }

    @Test void killsInTheSameTickEachShowTheirBannerInTurn() {
        var t = new KillBannerTimeline();
        assertTrue(t.age(0) < 0, "nothing shown before a kill");
        for (int i = 0; i < 5; i++) t.kill(S, 45 * S, false, PICK);
        assertEquals(5, t.streak(), "all five counted at once");
        assertEquals(List.of(1, 2, 3, 4, 5), play(t, S, 2), "then shown 1 to 5, none dropped");
        assertEquals(0, t.queued());
    }

    @Test void theNextKillInterruptsThePlayingBanner() {
        var t = new KillBannerTimeline();
        t.kill(S, 45 * S, false, PICK);
        assertTrue(t.next(S) && t.sequence() == 1);
        t.kill(S + 100_000_000L, 45 * S, true, PICK); // two ticks later, while banner 1 is still opening
        assertFalse(t.next(S + 100_000_000L), "banner 1 gets its moment");
        assertTrue(t.next(S + GAP) && t.sequence() == 2 && t.headshot(), "then banner 2 replaces it");
        assertEquals(0, t.age(S + GAP), 1e-9, "playing from its start");
        t.kill(3 * S, 45 * S, false, PICK);
        assertTrue(t.next(3 * S) && t.sequence() == 3, "a later kill replaces the playing banner at once");
    }

    @Test void streakTimesOutUnlessUnlimitedAndEndsOnDeath() {
        var t = new KillBannerTimeline();
        t.kill(0, 45 * S, false, PICK);
        t.kill(44 * S, 45 * S, false, PICK);
        assertEquals(2, t.streak(), "44 s later: still the streak (45 s default)");
        t.kill(90 * S, 45 * S, false, PICK);
        assertEquals(1, t.streak(), "46 s later: a new streak");
        t.kill(93 * S, 3 * S, false, PICK);
        assertEquals(2, t.streak(), "a custom 3 s: exactly 3 s still counts");
        t.kill(96 * S + 1, 3 * S, false, PICK);
        assertEquals(1, t.streak(), "just over 3 s: a new streak");
        t.kill(4000 * S, -1, false, PICK);
        assertEquals(2, t.streak(), "Unlimited: an hour later still counts");

        var flood = new KillBannerTimeline();
        for (int i = 0; i < 8; i++) flood.kill(S, -1, false, PICK);
        assertEquals(8, flood.streak(), "past 5 the streak keeps counting");
        assertEquals(List.of(1, 2, 3, 4, 5), play(flood, S, 3), "a flood keeps the first five banners waiting");
        assertEquals(5, flood.sequence(), "the banner stops at 5");

        var death = new KillBannerTimeline();
        death.kill(S, -1, false, PICK);
        death.kill(S, -1, false, PICK);
        death.endStreak(); // died right after those two kills: their banners still play
        death.kill(2 * S, -1, false, PICK);
        assertEquals(1, death.streak(), "death ends the streak, Unlimited too");
        assertEquals(List.of(1, 2, 1), play(death, S, 2));

        death.kill(4 * S, -1, false, PICK);
        death.clear(); // death screen, a new world or server
        assertEquals(0, death.streak());
        assertEquals(0, death.queued());
        assertTrue(death.age(4 * S) < 0);
    }

    @Test void previewsShowAtOnceAndLeaveTheStreakAlone() {
        var t = new KillBannerTimeline();
        t.kill(S, 45 * S, false, PICK);
        t.next(S);
        t.trigger(3, S + 1, true);
        assertTrue(t.preview() && t.sequence() == 3 && t.streak() == 1, "explicit preview is marked");
        t.kill(S + 2, 45 * S, false, PICK);
        assertTrue(t.next(S + 2) && !t.preview() && t.sequence() == 2, "a kill replaces a preview at once and continues the streak");
        t.trigger(Integer.MAX_VALUE, S + 3, false);
        assertEquals(5, t.sequence(), "capped without overflow");
    }

    /** Through KillBanners: each banner plays its own sound when it shows, kill_1 then kill_2 then kill_3. */
    @Test void eachQueuedBannerPlaysItsSoundOnItsTurn() {
        var module = new KillBannerModule();
        module.mobs.set(true);
        KillBanners.reset();
        var kill = new KillDetector.Kill(KillDetector.Kind.MOB, "", false);
        List<String> sounds = new ArrayList<>();
        for (int i = 0; i < 3; i++) sounds.add(KillBanners.fire(module, kill, S));
        for (long now = S + 50_000_000L; now <= 2 * S; now += 50_000_000L) {
            String sound = KillBanners.poll(module, now);
            if (sound != null) sounds.add(sound);
        }
        assertEquals(java.util.Arrays.asList("theladscore:reaver_kill_1", null, null, "theladscore:reaver_kill_2", "theladscore:reaver_kill_3"), sounds);
        KillBanners.reset();
    }

    @Test void streakSettings() {
        var module = new KillBannerModule();
        assertEquals(45, module.streakReset.get(), "45 s by default (1.7.1 had a fixed 8 s, never saved)");
        assertEquals(45 * S, module.streakWindow());
        for (String invalid : new String[] {"", "abc", "0", "601", "2.5", "-5", "99999999999"}) {
            module.streakReset.setValue(invalid);
            assertEquals(45, module.streakReset.get(), "'" + invalid + "' reverts");
            assertFalse(module.streakReset.valid(invalid));
        }
        module.streakReset.setValue(" 12 ");
        assertEquals("12", module.streakReset.getValue());
        assertEquals(new JsonPrimitive(12), module.streakReset.save(), "saved as a number");
        module.streakReset.load(new JsonPrimitive("30"));
        assertEquals(30, module.streakReset.get(), "a number saved as text loads");
        module.streakReset.load(new JsonPrimitive(7));
        assertEquals(7, module.streakReset.get());
        module.streakReset.load(new JsonPrimitive("lots"));
        assertEquals(7, module.streakReset.get(), "a broken saved value keeps the current one");
        module.unlimitedStreak.set(true);
        assertEquals(-1, module.streakWindow(), "Unlimited: never");
        module.getOptions().forEach(com.thelads.core.config.Option::reset);
        assertEquals(45 * S, module.streakWindow());
        assertSame(module.streakReset, module.getOption("Streak Reset (seconds)"), "stable saved names");
        assertSame(module.unlimitedStreak, module.getOption("Unlimited Streak"));
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
