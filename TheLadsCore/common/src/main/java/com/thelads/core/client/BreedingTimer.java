package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Breeding Timer (Badlion's Survival Breeding Timer): the 5 minute breeding cooldown over animals the player saw breed. The server keeps
 * that cooldown to itself, so the module is gated (HypixelSafetyManager risky set). Modern servers send the love hearts (entity event 18)
 * when an animal is fed and again for the parent when it breeds: a second hearts event within an animal's 30 s of love is its breed, and
 * its partner is the nearest other animal of its kind still in love. 1.8.9 syncs the sign of an animal's age instead, which turns from 0
 * to 1 when it breeds. The adapters track and draw; this class holds the rules and each animal's {@link State}.
 */
public final class BreedingTimer {
    public static final String NAME = "Breeding Timer";
    /** Vanilla's love time and breeding cooldown, in game ticks. */
    public static final long LOVE_TICKS = 600, COOLDOWN_TICKS = 6000;
    /** The module is on and the server rules allow it: cached once a client tick. */
    public static boolean on;
    private static Module module;

    private BreedingTimer() {}

    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
    }

    /** A hearts event at {@code now} on an animal whose last one was at {@code loveAt} (-1: none) is its breed. */
    public static boolean bred(long loveAt, long now) {
        return loveAt >= 0 && now > loveAt && now - loveAt <= LOVE_TICKS;
    }

    /** Whole seconds until {@code readyAt} (game ticks), rounded up; 0 once it can breed again. */
    public static int secondsLeft(long readyAt, long now) {
        return readyAt <= now ? 0 : (int) ((readyAt - now + 19) / 20);
    }

    public static String label(int seconds) {
        int s = seconds % 60;
        return "❤ " + seconds / 60 + (s < 10 ? ":0" : ":") + s;
    }

    /** One animal's love and cooldown (game ticks), kept on the animal by the adapters' mixins. */
    public static final class State {
        long loveAt = -1, readyAt;
        private int seconds = -1;
        private String label;

        /** Entity event 18 at {@code now}: true when the animal just bred (the adapter then starts its partner's cooldown). */
        public boolean hearts(long now) {
            if (bred(loveAt, now)) {
                bredAt(now);
                return true;
            }
            loveAt = now;
            return false;
        }

        public boolean inLove(long now) { return loveAt >= 0 && now >= loveAt && now - loveAt <= LOVE_TICKS; }

        public void bredAt(long now) {
            readyAt = now + COOLDOWN_TICKS;
            loveAt = -1;
        }

        public boolean cooling(long now) { return readyAt > now; }

        /** "♥ m:ss" left, rebuilt once a second rather than every frame. */
        public String label(long now) {
            int left = secondsLeft(readyAt, now);
            if (left != seconds) {
                seconds = left;
                label = BreedingTimer.label(left);
            }
            return label;
        }
    }

    /** The adapters' animal mixins: each animal's state, made on first use. */
    public interface Tracked {
        State lads$breeding();
    }
}
