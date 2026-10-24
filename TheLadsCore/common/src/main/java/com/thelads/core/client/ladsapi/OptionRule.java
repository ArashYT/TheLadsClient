package com.thelads.core.client.ladsapi;

/**
 * One option's server rule, already validated against the option (PROTOCOL.md "options"). Immutable; NaN/null = not set.
 * The GUI reads it for the lock icon and tooltip ("Limited by Lads SMP: max 10%").
 */
public final class OptionRule {
    /** Forced bool (Bool / PlayerAction options), or null. */
    public final Boolean bool;
    /** Forced slider value, or NaN. */
    public final double value;
    /** Slider limits, or NaN. */
    public final double min, max;
    /** Forced dropdown choice, or null. */
    public final String choice;
    /** Allowed dropdown choices, or null. */
    public final String[] choices;
    /** Forced ARGB color (alpha already >= 0x40), or null. */
    public final Integer color;
    /** Greyed out in the GUI (always true when a value is forced). */
    public final boolean lock;

    public OptionRule(Boolean bool, double value, double min, double max, String choice, String[] choices, Integer color, boolean lock) {
        this.bool = bool;
        this.value = value;
        this.min = min;
        this.max = max;
        this.choice = choice;
        this.choices = choices;
        this.color = color;
        this.lock = lock || bool != null || !Double.isNaN(value) || choice != null || color != null;
    }

    /** A forced value (refusable by the player), not only a limit. */
    public boolean forcesValue() {
        return bool != null || !Double.isNaN(value) || choice != null || color != null;
    }
}
