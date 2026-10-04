package com.thelads.core.client;

import java.util.Locale;

/**
 * Lads' own hunger arithmetic for the AppleSkin module, shared by every game version: what eating a food leaves, how much of
 * a hunger icon a value fills, the preview pulse, and the health natural regeneration gives back. It models the game's
 * documented food rules (hunger caps at 20, saturation at hunger, 4 exhaustion costs one saturation or hunger point).
 */
public final class FoodPreview {
    public static final int MAX_FOOD = 20;
    public static final float MAX_EXHAUSTION = 4;
    private FoodPreview() {}

    public static int foodAfter(int food, int nutrition) { return clamp(food + nutrition, 0, MAX_FOOD); }

    /** Saturation never passes the hunger level it is eaten at. */
    public static float saturationAfter(float saturation, float gained, int foodAfter) {
        return Math.max(0, Math.min(saturation + gained, foodAfter));
    }

    /** How much of hunger icon {@code slot} (two points each, slot 0 first) {@code value} fills, 0 to 1. */
    public static float iconFill(float value, int slot) { return Math.max(0, Math.min(1, (value - slot * 2) / 2)); }

    /** Preview opacity: a smooth 0 to 1 and back every 24 ticks. */
    public static float pulse(float ticks) { return (float) (0.5 - 0.5 * Math.cos(ticks * Math.PI / 12)); }

    /** Health a Regeneration effect heals: one point each time its interval (50 ticks, halved per level) divides the time left. */
    public static float regenerationEffect(int durationTicks, int amplifier) {
        int interval = amplifier >= 31 ? 0 : 50 >> Math.max(0, amplifier);
        return Math.max(0, interval > 0 ? durationTicks / interval : durationTicks);
    }

    /**
     * Health natural regeneration gives back from this food state until hunger drops below 18 or {@code missing} health is
     * healed, as the game's food tick spends it. Modern rules: with a full bar and saturation it heals up to 1 health per 6
     * saturation spent; otherwise 1 health per 6 exhaustion at 18 hunger or more. {@code legacy} (1.8.9): no fast healing and
     * 3 exhaustion per health.
     */
    public static float regenerated(int food, float saturation, float exhaustion, float missing, boolean legacy) {
        if (!(missing > 0) || !Float.isFinite(saturation) || !Float.isFinite(exhaustion)) return 0;
        food = clamp(food, 0, MAX_FOOD);
        saturation = Math.max(0, Math.min(saturation, MAX_FOOD));
        exhaustion = Math.max(0, Math.min(exhaustion, 40));
        float healed = 0;
        // ponytail: a step per heal or exhaustion drain; the guard bounds tiny-saturation crawls (about 4000 steps at worst).
        for (int guard = 0; guard < 100_000 && healed < missing; guard++) {
            if (exhaustion > MAX_EXHAUSTION) {
                exhaustion -= MAX_EXHAUSTION;
                if (saturation > 0) saturation = Math.max(saturation - 1, 0);
                else food--;
            } else if (!legacy && saturation > 0 && food >= MAX_FOOD) {
                float spent = Math.min(saturation, 6);
                healed += spent / 6;
                exhaustion = Math.min(exhaustion + spent, 40);
            } else if (food >= 18) {
                healed += 1;
                exhaustion = Math.min(exhaustion + (legacy ? 3 : 6), 40);
            } else break;
        }
        return Math.min(healed, missing);
    }

    /** The F3 line. Off the integrated server the game sends no exhaustion and saturation only now and then: marked "?". */
    public static String debugLine(int food, float saturation, float exhaustion, boolean exact) {
        return "Food: " + food + " hunger, " + String.format(Locale.ROOT, "%.2f", saturation) + (exact ? "" : "?") + " saturation, "
            + (exact ? String.format(Locale.ROOT, "%.2f", exhaustion) : "?") + "/4 exhaustion";
    }

    public static String number(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
