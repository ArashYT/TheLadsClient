package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;

/**
 * OneClient EvergreenHUD parity Custom Shape HUD element.
 * Renders configurable rectangle, border, or geometric overlay.
 * Visual-only.
 */
public class CustomShapeHudElement extends HudElement {
    public static final String MODULE_NAME = "CustomShape";

    public CustomShapeHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        var mod = ModuleManager.getInstance().getModule(MODULE_NAME);
        int w = 100, h = 40;
        if (mod != null) {
            Option ow = mod.getOption("Width");
            if (ow instanceof SliderOption so) w = so.getIntValue();
            Option oh = mod.getOption("Height");
            if (oh instanceof SliderOption so) h = so.getIntValue();
        }
        width = Math.max(10, w);
        height = Math.max(10, h);
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        var mod = ModuleManager.getInstance().getModule(MODULE_NAME);

        int fillColor = 0x80000000;
        int borderColor = 0xFFFFFFFF;
        int borderThick = 1;

        if (mod != null) {
            Option fc = mod.getOption("Fill color");
            if (fc instanceof ColorOption co) fillColor = co.getColor();
            Option bc = mod.getOption("Border color");
            if (bc instanceof ColorOption co) borderColor = co.getColor();
            Option bt = mod.getOption("Border thickness");
            if (bt instanceof SliderOption so) borderThick = so.getIntValue();
        }

        // Fill background
        graphics.fill(x, y, x + width, y + height, fillColor);

        // Border
        if (borderThick > 0) {
            graphics.fill(x, y, x + width, y + borderThick, borderColor); // top
            graphics.fill(x, y + height - borderThick, x + width, y + height, borderColor); // bottom
            graphics.fill(x, y, x + borderThick, y + height, borderColor); // left
            graphics.fill(x + width - borderThick, y, x + width, y + height, borderColor); // right
        }
    }
}
