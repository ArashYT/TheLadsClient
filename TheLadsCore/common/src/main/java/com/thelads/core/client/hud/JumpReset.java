package com.thelads.core.client.hud;

/**
 * Jump Reset (Lunar's Knockback Trainer, Feather's Jump Reset): how well you jump on the tick a hit's knockback arrives, which cancels
 * most of it. Each version reports your jumps ({@link #jumped}, from the jump itself) and, once a client tick at its end, whether you
 * were hurt this tick and stand on the ground ({@link #tick}). A jump on the hit's tick is Perfect, one tick off Early or Late, a
 * grounded hit with no jump within {@link #WINDOW} ticks Missed. Read only: nothing is sent or pressed for you.
 */
public final class JumpReset {
    public static final int NONE = 0, PERFECT = 1, EARLY = 2, LATE = 3, MISSED = 4;
    /** Ticks either side of the hit a jump still counts for it. */
    static final int WINDOW = 3;
    private static final String[] NAMES = {"", "Perfect", "Early", "Late", "Missed"};

    private static int tick, jumpTick = -100, hitTick = -100;
    private static boolean jumpedNow, rated = true;
    /** The last rating, its ticks off the hit (negative early) and when (System.currentTimeMillis), the streak of Perfect ones. */
    public static int rating = NONE, offset, streak;
    public static long ratedAt;

    private JumpReset() {}

    public static String name(int rating) {
        return NAMES[rating];
    }

    /** Your player jumped (on the client thread, during this tick). */
    public static void jumped() {
        jumpedNow = true;
    }

    /** End of a client tick: hurt is true on the tick your hurt time rose (a hit landed), grounded whether you stood on the ground. */
    public static void tick(boolean hurt, boolean grounded, long millis) {
        tick++;
        if (jumpedNow) {
            jumpedNow = false;
            jumpTick = tick;
            if (!rated && tick - hitTick <= WINDOW) rate(tick - hitTick, millis); // late (or perfect when the hit came this tick too)
        }
        if (hurt) {
            hitTick = tick;
            boolean recentJump = tick - jumpTick <= WINDOW; // jumped this tick or just before: it left the ground for this hit
            rated = !grounded && !recentJump; // a hit in the air with no jump around it can't be jump-reset
            if (recentJump) rate(jumpTick - hitTick, millis); // this tick or early
        }
        if (!rated && tick - hitTick > WINDOW) rate(Integer.MIN_VALUE, millis);
    }

    private static void rate(int ticksOff, long millis) {
        rated = true;
        ratedAt = millis;
        offset = ticksOff == Integer.MIN_VALUE ? 0 : ticksOff;
        rating = ticksOff == Integer.MIN_VALUE ? MISSED : ticksOff == 0 ? PERFECT : ticksOff < 0 ? EARLY : LATE;
        streak = rating == PERFECT ? streak + 1 : 0;
    }

    /** Leaving a world. */
    public static void reset() {
        rating = NONE;
        streak = 0;
        rated = true;
        jumpedNow = false;
    }
}
