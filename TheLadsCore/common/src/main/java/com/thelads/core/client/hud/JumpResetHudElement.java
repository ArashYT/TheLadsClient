package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Jump Reset's result (JumpReset): the last hit's rating, how many ticks off the jump was, and the streak of Perfect ones. */
public class JumpResetHudElement extends TextHudElement {
    private static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555};
    private String text = "";
    private long cachedAt = -1;
    private int cachedOptions = -1, color = COLORS[0];

    public JumpResetHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int rating = JumpReset.rating;
        boolean timing = optBool("Show Timing", true), streak = optBool("Show Streak", true);
        if (rating == JumpReset.NONE) {
            color = COLORS[JumpReset.PERFECT];
            return editor ? "Perfect (0 ms) x3" : "";
        }
        int options = (timing ? 1 : 0) | (streak ? 2 : 0);
        if (JumpReset.ratedAt != cachedAt || options != cachedOptions) {
            text = text(rating, JumpReset.offset, JumpReset.streak, timing, streak);
            color = COLORS[rating];
            cachedAt = JumpReset.ratedAt;
            cachedOptions = options;
        }
        return text;
    }

    /** "Late (+50 ms) x0": the rating, the jump's distance from the hit in ms (a tick is 50) and the Perfect streak. */
    static String text(int rating, int offset, int streak, boolean timing, boolean showStreak) {
        StringBuilder out = new StringBuilder(JumpReset.name(rating));
        if (timing && rating != JumpReset.MISSED) out.append(" (").append(offset > 0 ? "+" : "").append(offset * 50).append(" ms)");
        if (showStreak) out.append(" x").append(streak);
        return out.toString();
    }

    @Override
    protected int textColor() {
        return color;
    }
}
