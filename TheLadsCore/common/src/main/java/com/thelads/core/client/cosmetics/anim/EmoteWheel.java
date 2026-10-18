package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;

/**
 * The emote wheel: eight slots round a ring, picked by the pointer's direction from the centre. Hold the Emotes key, point, release:
 * the slot under the pointer plays. A quick tap leaves the wheel open for a click (Esc, the key again or a click outside closes it).
 * Each version hosts it in a plain screen and forwards render, release, click and key.
 * <p>
 * The ring is drawn as horizontal spans at half-pixel steps (LadsGraphics only fills rectangles), worked out once per size.
 */
public final class EmoteWheel {
    public static final int OUTER = 80, INNER = 30;
    /** A release this soon after opening is a tap: the wheel stays open for a click. */
    static final long TAP_MS = 250;
    private static final int SEP = 8, CENTER = 9, RIM = 10;
    private final long openedMs;
    private int[] spans = new int[0];
    private int spanCount, cx, cy;

    public EmoteWheel(long openedMs) { this.openedMs = openedMs; }

    /** The slot the pointer points at (its direction from the centre), or -1 in the middle. */
    public int hovered(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy < INNER * INNER * 0.5) return -1;
        double turns = Math.atan2(dx, -dy) / (2 * Math.PI);
        return (int) Math.floorMod(Math.round(turns * EmoteLibrary.WHEEL.length), (long) EmoteLibrary.WHEEL.length);
    }

    /** The Emotes key went up: the emote to play, "" to close, null to stay open (a tap). */
    public String release(long nowMs, double mx, double my) {
        String id = slot(hovered(mx, my));
        return id != null ? id : nowMs - openedMs < TAP_MS ? null : "";
    }

    /** A click: the emote to play, "" to close (outside the ring), null to stay open (an empty slot). */
    public String click(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy > (OUTER + 12) * (OUTER + 12)) return "";
        return slot(hovered(mx, my));
    }

    private static String slot(int i) {
        if (i < 0 || i >= EmoteLibrary.WHEEL.length) return null;
        String id = EmoteLibrary.WHEEL[i];
        return EmoteLibrary.owns(id) ? id : null;
    }

    public void render(LadsGraphics g, int mx, int my, long nowMs) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        if (cx != width / 2 || cy != height / 2 || spanCount == 0) layout(width / 2, height / 2);
        float open = Math.min(1, (nowMs - openedMs) / 140f), ease = 1 - (1 - open) * (1 - open) * (1 - open);
        int hover = hovered(mx, my);
        g.fill(0, 0, width, height, (int) (0x70 * ease) << 24);
        g.pushPose();
        g.translate(cx, cy);
        float scale = 0.86f + 0.14f * ease;
        g.scale(scale * 0.5f, scale * 0.5f);
        for (int i = 0; i < spanCount; i += 4) {
            int kind = spans[i + 3], color;
            if (kind < SEP) color = kind == hover ? LadsPalette.PRIMARY : (EmoteLibrary.WHEEL[kind] != null && EmoteLibrary.owns(EmoteLibrary.WHEEL[kind])) ? 0xF21B1015 : 0xE6130B10;
            else if (kind == SEP) color = LadsPalette.BORDER;
            else if (kind == CENTER) color = 0xF2070709;
            else color = kind - RIM == hover ? LadsPalette.ACCENT : LadsPalette.BORDER;
            g.fill(spans[i + 1], spans[i], spans[i + 2], spans[i] + 1, color);
        }
        g.scale(2, 2);
        String[] wheel = EmoteLibrary.WHEEL;
        int mid = (OUTER + INNER) / 2;
        for (int k = 0; k < wheel.length; k++) {
            double a = 2 * Math.PI * k / wheel.length;
            int x = (int) Math.round(Math.sin(a) * mid), y = (int) Math.round(-Math.cos(a) * mid);
            String id = wheel[k];
            Emote emote = EmoteLibrary.owns(id) ? EmoteLibrary.get(id) : null;
            if (emote == null) { g.drawCenteredText("Empty", x, y - g.fontHeight() / 2, LadsPalette.DISABLED, false); continue; }
            note(g, x - 3, y - 13, k == hover ? LadsPalette.TEXT : LadsPalette.ACCENT);
            g.drawCenteredText(emote.name, x, y + 1, LadsPalette.TEXT, true);
        }
        String hoveredId = hover < 0 || hover >= wheel.length ? null : wheel[hover];
        Emote picked = EmoteLibrary.owns(hoveredId) ? EmoteLibrary.get(hoveredId) : null;
        g.drawCenteredText(picked == null ? "Emotes" : picked.name, 0, -g.fontHeight() / 2, picked == null ? LadsPalette.MUTED : LadsPalette.ACCENT, true);
        g.popPose();
        g.drawCenteredText("Release over an emote to play it, Esc closes", cx, cy + OUTER + 10, LadsPalette.MUTED, true);
    }

    /** A small eighth note, 7 x 10. */
    private static void note(LadsGraphics g, int x, int y, int color) {
        g.fill(x + 4, y, x + 5, y + 8, color);
        g.fill(x + 5, y + 1, x + 6, y + 2, color);
        g.fill(x + 6, y + 2, x + 7, y + 4, color);
        g.fill(x + 1, y + 7, x + 4, y + 10, color);
        g.fill(x, y + 8, x + 5, y + 9, color);
    }

    /** Spans (y, x0, x1, kind) in half pixels round the centre. */
    private void layout(int centerX, int centerY) {
        cx = centerX; cy = centerY;
        int r = 2 * (OUTER + 1), slots = EmoteLibrary.WHEEL.length;
        spanCount = 0;
        for (int y = -r; y < r; y++) {
            int runKind = -1, runStart = 0;
            for (int x = -r; x <= r; x++) {
                int kind = x == r ? -1 : kind(x + 0.5, y + 0.5, slots);
                if (kind == runKind) continue;
                if (runKind >= 0) add(y, runStart, x, runKind);
                runKind = kind; runStart = x;
            }
        }
    }

    private static int kind(double x, double y, int slots) {
        double d = Math.sqrt(x * x + y * y) / 2;
        if (d > OUTER + 1) return -1;
        if (d < INNER - 1) return CENTER;
        double turns = Math.atan2(x, -y) / (2 * Math.PI) * slots + 0.5;
        int slot = (int) Math.floorMod((long) Math.floor(turns), (long) slots);
        if (d > OUTER - 1) return RIM + slot;
        if (d < INNER) return SEP;
        double edge = Math.abs(turns - Math.round(turns)) * 2 * Math.PI / slots * d;
        return edge < 0.6 ? SEP : slot;
    }

    private void add(int y, int x0, int x1, int kind) {
        if (spanCount + 4 > spans.length) spans = java.util.Arrays.copyOf(spans, Math.max(1024, spans.length * 2));
        spans[spanCount++] = y; spans[spanCount++] = x0; spans[spanCount++] = x1; spans[spanCount++] = kind;
    }
}
