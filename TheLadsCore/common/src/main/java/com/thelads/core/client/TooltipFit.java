package com.thelads.core.client;

/**
 * Tooltips taller or wider than the screen (long lore, NBT lines, server menus) are drawn smaller so the whole box fits, the same
 * on every version: each version measures its tooltip as it lays it out, then draws it unchanged in a space this much larger than
 * the screen (its own placement rules keep it on screen there), scaled back down about the screen's origin.
 */
public final class TooltipFit {
    /** Room the box needs around its text: the 4-pixel frame on each side, plus 1.8.9 Forge's 6-pixel bottom margin. */
    static final int PAD = 10;
    /** Never below half size, nor below one screen pixel per GUI pixel. */
    static final float MIN = 0.5F;

    /** QA: the scale of the last tooltip drawn. */
    public static volatile float last = 1F;

    private TooltipFit() {}

    /** The scale (1: as is) for a tooltip whose text box is {@code width} x {@code height} on a screen of that GUI size. */
    public static float scale(int width, int height, int screenWidth, int screenHeight, int guiScale) {
        float fit = Math.min(1F, Math.min(screenWidth / (float) (width + PAD), screenHeight / (float) (height + PAD)));
        last = fit >= 1F ? 1F : Math.max(fit, Math.max(MIN, 1F / Math.max(1, guiScale)));
        return last;
    }
}
