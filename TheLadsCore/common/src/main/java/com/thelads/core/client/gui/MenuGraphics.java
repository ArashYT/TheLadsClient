package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;

final class MenuGraphics {
    static final int BG = LadsPalette.BACKGROUND, PANEL = LadsPalette.PANEL, CARD = LadsPalette.CARD;
    static final int TEXT = LadsPalette.TEXT, MUTED = LadsPalette.MUTED, ACCENT = LadsPalette.ACCENT;
    private MenuGraphics() {}
    static int mix(int from, int to, float progress) {
        float t = Math.max(0, Math.min(1, progress));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= Math.round(((from >>> shift) & 255) * (1 - t) + ((to >>> shift) & 255) * t) << shift;
        return result;
    }
    static void round(LadsGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(4, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = i == 0 ? 2 : i == 1 ? 1 : 0;
            g.fill(x + inset, y + i, x + r, y + i + 1, color);
            g.fill(x + w - r, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + r, y + h - i, color);
            g.fill(x + w - r, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }
    static String fit(LadsGraphics g, String text, int width) {
        if (width <= 0) return "";
        if (g.textWidth(text) <= width) return text;
        int low = 1, high = text.length(), best = 0;
        while (low <= high) {
            int middle = (low + high) >>> 1, end = middle;
            if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            if (g.textWidth(text.substring(0, end) + "...") <= width) {
                best = end;
                low = middle + 1;
            } else high = middle - 1;
        }
        return best == 0 ? "" : text.substring(0, best) + "...";
    }
    static int wrap(LadsGraphics g, String text, int x, int y, int width, int maxLines, int color) {
        String line = ""; int row = 0;
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (g.textWidth(candidate) > width && !line.isEmpty()) {
                g.drawText(fit(g, line, width), x, y + row * 12, color);
                if (++row >= maxLines) return row * 12;
                line = word;
            } else line = candidate;
        }
        if (row < maxLines && !line.isEmpty()) g.drawText(fit(g, line, width), x, y + row++ * 12, color);
        return row * 12;
    }
}
