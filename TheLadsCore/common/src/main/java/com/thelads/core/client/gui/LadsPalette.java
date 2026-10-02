package com.thelads.core.client.gui;

/** Lads chrome colors; primary states match TheLadsLauncher/MainWindow.axaml. */
public final class LadsPalette {
    public static final int BACKGROUND = 0xFF070709;
    public static final int PANEL = 0xFF130B10;
    public static final int CARD = 0xFF1B1015;
    public static final int BORDER = 0xFF44202A;
    public static final int HOVER = 0xFF30161E;
    public static final int PRIMARY = 0xFF8B0000;
    public static final int PRIMARY_HOVER = 0xFFB00000;
    public static final int PRIMARY_PRESSED = 0xFF600000;
    public static final int ACCENT = 0xFFFF6666;
    public static final int TEXT = 0xFFFFF1F1;
    public static final int MUTED = 0xFFC1A6AB;
    public static final int DISABLED = 0xFF795A62;
    /** Module cards: on = green, off = red (white text on both); the glow tints the edge, hover fill and halo. */
    public static final int CARD_ON = 0xFF1C5A2E, CARD_ON_GLOW = 0xFF5DF08A, CARD_OFF = 0xFF5E1A21, CARD_OFF_GLOW = ACCENT;
    private LadsPalette() {}
}
