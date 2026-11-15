package com.thelads.core.client.hud;

/** The HUD editor's mouse-wheel resize: a HUD's size in percent moves one notch on a 25% grid, or 5% with Control held. */
public final class HudResize {
    public static final int STEP = 25, FINE_STEP = 5;
    private HudResize() {}

    /**
     * The size one wheel notch from {@code percent}: up or down to the next multiple of the step (100 goes to 125 or 75, 110 to
     * 125 or 100), kept inside [min, max]. A size already at that limit stays.
     */
    public static int next(double percent, boolean up, boolean fine, double min, double max) {
        int step = fine ? FINE_STEP : STEP;
        double notches = percent / step, whole = Math.rint(notches);
        double target = Math.abs(notches - whole) < 1e-6 ? whole + (up ? 1 : -1) : up ? Math.ceil(notches) : Math.floor(notches);
        return (int) Math.round(Math.max(min, Math.min(max, target * step)));
    }
}
