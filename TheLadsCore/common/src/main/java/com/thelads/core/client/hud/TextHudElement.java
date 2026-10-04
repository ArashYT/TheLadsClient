package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** One measured text snapshot per frame, shared by placement and drawing. */
abstract class TextHudElement extends HudElement {
    private final int minimumWidth;
    private LadsGraphics preparedGraphics;
    private String preparedText = "";
    private int preparedTextWidth;
    private Object metricsKey;
    private String measuredText;
    /** updateText is measuring for the HUD editor or a menu preview, where a sample may stand in for empty content. */
    protected boolean editor;

    protected TextHudElement(int minimumWidth) { this.minimumWidth = minimumWidth; }
    protected abstract String updateText(LadsGraphics graphics);
    protected int textColor() { return resolveColor(); }

    @Override public final void prepareRender(LadsGraphics graphics, boolean editor) {
        this.editor = editor;
        preparedText = updateText(graphics);
        Object key = graphics.textMetricsKey();
        if (key == null || key != metricsKey || !preparedText.equals(measuredText)) {
            preparedTextWidth = graphics.textWidth(preparedText);
            measuredText = preparedText;
            metricsKey = key;
        }
        width = Math.max(minimumWidth, preparedTextWidth + 12);
        height = Math.max(16, graphics.fontHeight() + 6);
        preparedGraphics = graphics;
    }

    @Override public final void render(LadsGraphics graphics) {
        if (preparedGraphics != graphics) prepareRender(graphics, false);
        preparedGraphics = null;
        if (preparedText == null || preparedText.isEmpty()) return;
        drawBackground(graphics);
        drawCenteredText(graphics, preparedText, textColor(), preparedTextWidth);
    }
}
