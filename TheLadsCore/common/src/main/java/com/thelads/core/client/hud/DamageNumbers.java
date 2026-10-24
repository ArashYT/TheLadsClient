package com.thelads.core.client.hud;

/**
 * Damage Indicator's floating damage numbers: each version reports the health of the living entities near the player once a tick
 * ({@link #observe}, then {@link #endTick}); a drop spawns a number at the entity's head that floats up and fades over
 * HudExtras.numberMillis, drawn in the world by the version (count/x/y/z/alpha/text). Fixed arrays, nothing allocated per frame.
 * Gated with the module: other players' exact health is information vanilla does not show.
 */
public final class DamageNumbers {
    static final int TRACKED = 64, SHOWN = 32;
    public static final String HEART = "\u2764";
    private static final int[] ids = new int[TRACKED];
    private static final float[] health = new float[TRACKED];
    private static final boolean[] seen = new boolean[TRACKED];
    private static int tracked;

    private static final double[] xs = new double[SHOWN], ys = new double[SHOWN], zs = new double[SHOWN];
    private static final long[] born = new long[SHOWN];
    private static final String[] texts = new String[SHOWN];
    private static int next;

    private DamageNumbers() {}

    /** An entity's health this tick at (x, y, z) (its head); a drop from last tick spawns a number. Returns the damage, 0 for none. */
    public static float observe(int id, float now, double x, double y, double z, long millis) {
        for (int i = 0; i < tracked; i++) {
            if (ids[i] != id) continue;
            float before = health[i];
            health[i] = now;
            seen[i] = true;
            float damage = before - now;
            if (damage > 0.01f) spawn(damage, x, y, z, millis);
            return damage > 0.01f ? damage : 0;
        }
        if (tracked < TRACKED) {
            ids[tracked] = id;
            health[tracked] = now;
            seen[tracked++] = true;
        }
        return 0;
    }

    /** After this tick's observe calls: entities not seen (gone, out of range) are forgotten. */
    public static void endTick() {
        int kept = 0;
        for (int i = 0; i < tracked; i++) {
            if (!seen[i]) continue;
            ids[kept] = ids[i];
            health[kept] = health[i];
            seen[kept++] = false;
        }
        tracked = kept;
    }

    /** Off, another world: nothing tracked or shown. */
    public static void clear() {
        tracked = 0;
        for (int i = 0; i < SHOWN; i++) texts[i] = null;
    }

    static void spawn(float damage, double x, double y, double z, long millis) {
        int slot = next;
        next = (next + 1) % SHOWN;
        xs[slot] = x + ((slot * 37) % 7 - 3) * 0.08; // spread hits on one target a little
        ys[slot] = y;
        zs[slot] = z + ((slot * 53) % 7 - 3) * 0.08;
        born[slot] = millis;
        texts[slot] = format(damage);
    }

    /** Hearts lost and a heart, with one decimal ("3.5"), a whole number without it ("4"). */
    static String format(float damage) {
        float hearts = Math.round(damage * 5) / 10f; // half points: 7 damage = 3.5 hearts
        return (hearts == (int) hearts ? Integer.toString((int) hearts) : Float.toString(hearts)) + HEART;
    }

    public static int slots() {
        return SHOWN;
    }

    /** The slot's text, or null when it shows nothing now. */
    public static String text(int slot, long millis, int lifeMillis) {
        String text = texts[slot];
        if (text != null && millis - born[slot] > lifeMillis) texts[slot] = text = null;
        return text;
    }

    public static double x(int slot) {
        return xs[slot];
    }

    /** Rises 0.75 blocks over its life. */
    public static double y(int slot, long millis, int lifeMillis) {
        return ys[slot] + 0.75 * Math.min(1, (millis - born[slot]) / (double) lifeMillis);
    }

    public static double z(int slot) {
        return zs[slot];
    }

    /** Fully opaque for the first 60% of its life, then fading out (0 to 255, never below 4: fonts skip alpha under 4). */
    public static int alpha(int slot, long millis, int lifeMillis) {
        double life = (millis - born[slot]) / (double) lifeMillis;
        return life < 0.6 ? 255 : Math.max(4, (int) (255 * (1 - life) / 0.4));
    }
}
