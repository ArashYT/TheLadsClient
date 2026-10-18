package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.modules.CosmeticsModule;

/**
 * The emote wheel: eight slots round a ring, picked by the pointer's direction from the centre. Hold the Emotes key, point, release:
 * the slot under the pointer plays. A quick tap leaves the wheel open for a click (Esc, the key again or a click outside closes it).
 * Each version hosts it in a plain screen and forwards render, release, click and key. The Cosmetics screen draws the same wheel
 * (renderAt) to pick which emote sits in which slot.
 * <p>
 * The ring is drawn as horizontal spans at half-pixel steps (LadsGraphics only fills rectangles), worked out once per size.
 */
public final class EmoteWheel {
    public static final int OUTER = 80, INNER = 30;
    /** A release this soon after opening is a tap: the wheel stays open for a click. */
    static final long TAP_MS = 250;
    private static final int SEP = 8, CENTER = 9, RIM = 10;
    private final long openedMs;
    private final String[] slots;
    private int[] spans = new int[0];
    private int spanCount, cx, cy;

    /** The player's wheel (Cosmetics screen slots). */
    public EmoteWheel(long openedMs) { this(openedMs, CosmeticsModule.get().slots()); }

    public EmoteWheel(long openedMs, String[] slots) { this.openedMs = openedMs; this.slots = slots; }

    /** The slot the pointer points at (its direction from the centre), or -1 in the middle. */
    public int hovered(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy < INNER * INNER * 0.5) return -1;
        double turns = Math.atan2(dx, -dy) / (2 * Math.PI);
        return (int) Math.floorMod(Math.round(turns * slots.length), (long) slots.length);
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

    private String slot(int i) { return i < 0 ? null : slots[i]; }

    /** The emote wheel over the world: dimmed backdrop, centred, opening animation, hint below. */
    public void render(LadsGraphics g, int mx, int my, long nowMs) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        float open = Math.min(1, (nowMs - openedMs) / 140f), ease = 1 - (1 - open) * (1 - open) * (1 - open);
        g.fill(0, 0, width, height, (int) (0x70 * ease) << 24);
        draw(g, width / 2, height / 2, mx, my, ease, -1);
        g.drawCenteredText("Release over an emote to play it, Esc closes", cx, cy + OUTER + 10, LadsPalette.MUTED, true);
    }

    /** Cosmetics screen: the wheel at (x, y) with {@code selected} lit (-1 none); hovered() then reads this position. */
    public void renderAt(LadsGraphics g, int x, int y, int mx, int my, int selected) { draw(g, x, y, mx, my, 1, selected); }

    private void draw(LadsGraphics g, int x, int y, int mx, int my, float ease, int selected) {
        if (cx != x || cy != y || spanCount == 0) layout(x, y);
        int hover = hovered(mx, my);
        g.pushPose();
        g.translate(cx, cy);
        float scale = 0.86f + 0.14f * ease;
        g.scale(scale * 0.5f, scale * 0.5f);
        for (int i = 0; i < spanCount; i += 4) {
            int kind = spans[i + 3], color;
            if (kind < SEP) color = kind == hover ? LadsPalette.PRIMARY : kind == selected ? LadsPalette.PRIMARY_PRESSED
                : slots[kind] != null ? 0xF21B1015 : 0xE6130B10;
            else if (kind == SEP) color = LadsPalette.BORDER;
            else if (kind == CENTER) color = 0xF2070709;
            else color = kind - RIM == hover || kind - RIM == selected ? LadsPalette.ACCENT : LadsPalette.BORDER;
            g.fill(spans[i + 1], spans[i], spans[i + 2], spans[i] + 1, color);
        }
        g.scale(2, 2);
        String[] wheel = slots;
        int mid = (OUTER + INNER) / 2;
        for (int k = 0; k < wheel.length; k++) {
            double a = 2 * Math.PI * k / wheel.length;
            int lx = (int) Math.round(Math.sin(a) * mid), ly = (int) Math.round(-Math.cos(a) * mid);
            Emote emote = EmoteLibrary.get(wheel[k]);
            if (emote == null) { g.drawCenteredText("Empty", lx, ly - g.fontHeight() / 2, LadsPalette.DISABLED, false); continue; }
            note(g, lx - 3, ly - 13, k == hover || k == selected ? LadsPalette.TEXT : LadsPalette.ACCENT);
            g.drawCenteredText(emote.name, lx, ly + 1, LadsPalette.TEXT, true);
        }
        int shown = hover >= 0 ? hover : selected;
        Emote picked = shown < 0 ? null : EmoteLibrary.get(wheel[shown]);
        g.drawCenteredText(picked == null ? "Emotes" : picked.name, 0, -g.fontHeight() / 2, picked == null ? LadsPalette.MUTED : LadsPalette.ACCENT, true);
        g.popPose();
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
        int r = 2 * (OUTER + 1), count = slots.length;
        spanCount = 0;
        for (int y = -r; y < r; y++) {
            int runKind = -1, runStart = 0;
            for (int x = -r; x <= r; x++) {
                int kind = x == r ? -1 : kind(x + 0.5, y + 0.5, count);
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
