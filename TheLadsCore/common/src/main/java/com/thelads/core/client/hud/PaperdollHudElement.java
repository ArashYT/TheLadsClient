package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.DropdownOption;

public class PaperdollHudElement extends HudElement {
    public PaperdollHudElement() {
        this.x = 20;
        this.y = 20;
        this.width = 70;
        this.height = 70;
    }

    @Override
    public void render(LadsGraphics g) {
        g.drawPlayerModel(x, y, width, height, false);
    }

    @Override
    public void renderEditor(LadsGraphics g) {
        if (g.getGame() != null && g.getGame().hasPlayer()) {
            g.drawPlayerModel(x, y, width, height, true);
        } else {
            drawBackground(g);
            int pad = 4;
            int boxX = x + pad, boxY = y + pad, boxW = width - 2 * pad, boxH = height - 2 * pad;
            int headSize = Math.max(14, Math.min(28, boxH / 4));
            int headX = boxX + (boxW - headSize) / 2;
            int headY = boxY + 2;
            g.drawHead("Steve", "", headX, headY, headSize);
            int bodyW = headSize;
            int bodyH = headSize * 6 / 5;
            int bodyX = headX;
            int bodyY = headY + headSize + 2;
            g.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, 0xAA00AAFF);
            int armW = headSize / 2;
            g.fill(bodyX - armW - 2, bodyY, bodyX - 2, bodyY + bodyH, 0xAA0088DD);
            g.fill(bodyX + bodyW + 2, bodyY, bodyX + bodyW + armW + 2, bodyY + bodyH, 0xAA0088DD);
            int legW = headSize / 2 - 1;
            int legH = bodyH;
            int legY = bodyY + bodyH + 2;
            g.fill(bodyX, legY, bodyX + legW, legY + legH, 0xAA0000AA);
            g.fill(bodyX + bodyW - legW, legY, bodyX + bodyW, legY + legH, 0xAA0000AA);
            g.drawCenteredText("Paperdoll", x + width / 2, y + height - 10, com.thelads.core.client.gui.LadsPalette.TEXT);
        }
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        width = height = (int) Math.round(number("Model Scale", 4)) * 5 * 7 / 2;
        screenWidth = g.getScaledWidth(); screenHeight = g.getScaledHeight();
    }

    private int screenWidth, screenHeight;
    private double number(String name, double fallback) {
        var module = ModuleManager.getInstance().getModule("Paperdoll");
        return module != null && module.getOption(name) instanceof SliderOption option ? option.getValue() : fallback;
    }
    private int anchor() { return optCycle("Anchor", 0); }
    private int originX() { return anchor() == 0 ? x : (screenWidth - getRenderWidth()) * ((anchor() - 1) % 3) / 2; }
    private int originY() { return anchor() == 0 ? y : (screenHeight - getRenderHeight()) * ((anchor() - 1) / 3) / 2; }
    @Override public int getDisplayX(LadsGraphics g) { return originX() + (int) number("X Offset", 0); }
    @Override public int getDisplayY(LadsGraphics g) { return originY() + (int) number("Y Offset", 0); }

    @Override public void setDisplayPosition(int displayX, int displayY) {
        // Dragging takes ownership from the selected anchor while preserving module offsets.
        var module = ModuleManager.getInstance().getModule("Paperdoll");
        if (module != null && module.getOption("Anchor") instanceof DropdownOption option) option.setIndex(0);
        setPosition(displayX - (int) number("X Offset", 0), displayY - (int) number("Y Offset", 0));
    }

    @Override public boolean isAvailable() { return LadsGameBridge.get().hasPaperDollRenderer(); }
}
