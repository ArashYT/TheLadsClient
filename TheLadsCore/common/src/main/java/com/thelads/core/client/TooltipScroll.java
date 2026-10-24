package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Scrollable Tooltips (Lunar's Scrollable Tooltips, OneClient's Tooltip Scroll): a tooltip taller than the screen, even after
 * TooltipFit made it as small as it goes, scrolls with the mouse wheel; one wider than the screen scrolls sideways with Shift held.
 * Each version passes where its tooltip positioner put the tooltip ({@link #place}) and offers every wheel turn in a screen to
 * {@link #scroll} first. The wheel is only taken while such a tooltip shows, so creative tabs, lists and Mouse Tweaks keep it
 * otherwise. A different tooltip starts unscrolled. Visual only. Render and client thread only.
 */
public final class TooltipScroll {
    public static final String NAME = "Scrollable Tooltips", SPEED = "Scroll Speed", INVERT = "Invert Scroll", SIDEWAYS = "Shift Scrolls Sideways";
    /** Room kept between the tooltip's text and the screen edge: its frame and a pixel. */
    static final int MARGIN = 4;
    /** A tooltip not drawn for this long is gone: the wheel goes back to the screen and the next tooltip starts unscrolled. */
    static final long GONE = 150_000_000L;
    public static boolean on;
    static boolean invert, sideways = true;
    static int speed = 3;
    /** Where the last tooltip is drawn, set by {@link #place}. */
    public static int x, y;
    private static int offsetX, offsetY, lastWidth, lastHeight;
    private static boolean taller, wider;
    private static long drawnAt = Long.MIN_VALUE / 2;
    private static Module module;

    private TooltipScroll() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
        if (!on) return;
        speed = module.getOption(SPEED) instanceof SliderOption s ? (int) s.getValue() : 3;
        invert = module.getOption(INVERT) instanceof BoolOption b && b.get();
        sideways = !(module.getOption(SIDEWAYS) instanceof BoolOption b) || b.get();
    }

    /**
     * A tooltip whose text is {@code width} x {@code height}, placed by its positioner at ({@code px}, {@code py}) in a space of
     * {@code screenWidth} x {@code screenHeight}: sets {@link #x} and {@link #y} to where it is drawn now, scrolled but never past
     * the point where its far edge shows.
     */
    public static void place(int px, int py, int width, int height, int screenWidth, int screenHeight, long now) {
        x = px;
        y = py;
        if (!on) return;
        if (width != lastWidth || height != lastHeight || now - drawnAt > GONE) {
            offsetX = offsetY = 0;
            lastWidth = width;
            lastHeight = height;
        }
        drawnAt = now;
        wider = width + 2 * MARGIN > screenWidth;
        taller = height + 2 * MARGIN > screenHeight;
        if (wider) offsetX = (x = clamp(px + offsetX, screenWidth - MARGIN - width, MARGIN)) - px;
        if (taller) offsetY = (y = clamp(py + offsetY, screenHeight - MARGIN - height, MARGIN)) - py;
    }

    /**
     * A wheel turn ({@code amount} notches, up positive) while a screen is open: true when it scrolled the tooltip, and the screen
     * must not get it. Up moves the tooltip down, showing its top (Invert Scroll swaps that).
     */
    public static boolean scroll(double amount, boolean shift, long now) {
        if (!on || amount == 0 || now - drawnAt > GONE) return false;
        boolean side = shift && sideways;
        if (side ? !wider : !taller) return false;
        int step = (int) Math.round(amount * speed * 10);
        if (step == 0) step = amount > 0 ? 1 : -1;
        if (invert) step = -step;
        if (side) offsetX += step;
        else offsetY += step;
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
