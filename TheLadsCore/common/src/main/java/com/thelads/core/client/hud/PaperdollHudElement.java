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
    public void renderEditor(LadsGraphics g) { g.drawPlayerModel(x, y, width, height, true); }

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
