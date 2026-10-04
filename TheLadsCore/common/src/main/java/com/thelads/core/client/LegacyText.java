package com.thelads.core.client;

/** Section-sign formatting codes, the form the Lads HUD's String text keeps its colours in on every version. */
public final class LegacyText {
    private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private LegacyText() {}

    /** The colour code (0-9, a-f) of the chat colour nearest to rgb: exact for the 16 named colours, closest for any other. */
    public static char code(int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < COLORS.length; i++) {
            int dr = (rgb >> 16 & 255) - (COLORS[i] >> 16 & 255), dg = (rgb >> 8 & 255) - (COLORS[i] >> 8 & 255), db = (rgb & 255) - (COLORS[i] & 255);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return "0123456789abcdef".charAt(best);
    }
}
