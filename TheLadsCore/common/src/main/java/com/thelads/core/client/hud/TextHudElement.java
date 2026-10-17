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
            preparedTextWidth = 0;
            for (String line : preparedText.split("\n")) preparedTextWidth = Math.max(preparedTextWidth, graphics.textWidth(line));
            measuredText = preparedText;
            metricsKey = key;
        }
        width = Math.max(minimumWidth, preparedTextWidth + 12);
        height = Math.max(16, preparedText.split("\n").length * (graphics.fontHeight() + 2) + 4);
        preparedGraphics = graphics;
    }

    @Override public final void render(LadsGraphics graphics) {
        if (preparedGraphics != graphics) prepareRender(graphics, false);
        preparedGraphics = null;
        if (preparedText == null || preparedText.isEmpty()) return;
        drawBackground(graphics);
        if (preparedText.indexOf('\n') < 0) {
            drawCenteredText(graphics, preparedText, textColor(), preparedTextWidth);
            return;
        }
        // Several lines (Toggle Sprint & Sneak with both on): each centred, the block centred in the box.
        String[] lines = preparedText.split("\n");
        int lineHeight = graphics.fontHeight() + 2, top = y + (height - lines.length * lineHeight + 2) / 2 + 1;
        for (int i = 0; i < lines.length; i++)
            graphics.drawText(lines[i], x + (width - graphics.textWidth(lines[i])) / 2, top + i * lineHeight, textColor(),
                com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }
}
