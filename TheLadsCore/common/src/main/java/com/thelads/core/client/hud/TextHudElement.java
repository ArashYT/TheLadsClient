package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** One measured text snapshot per frame, shared by placement and drawing. */
abstract class TextHudElement extends HudElement {
    private final int minimumWidth;
    private LadsGraphics preparedGraphics;
    private String preparedText = "";
    private int preparedTextWidth;

    protected TextHudElement(int minimumWidth) { this.minimumWidth = minimumWidth; }
    protected abstract String updateText(LadsGraphics graphics);
    protected int textColor() { return resolveColor(); }

    @Override public final void prepareRender(LadsGraphics graphics, boolean editor) {
        preparedText = updateText(graphics);
        preparedTextWidth = graphics.textWidth(preparedText);
        width = Math.max(minimumWidth, preparedTextWidth + 12);
        height = Math.max(16, graphics.fontHeight() + 6);
        preparedGraphics = graphics;
    }

    @Override public final void render(LadsGraphics graphics) {
        if (preparedGraphics != graphics) prepareRender(graphics, false);
        preparedGraphics = null;
        drawBackground(graphics);
        drawCenteredText(graphics, preparedText, textColor(), preparedTextWidth);
    }
}
