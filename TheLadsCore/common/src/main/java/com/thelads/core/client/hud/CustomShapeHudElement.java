package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.SliderOption;

/** Custom Shape (OneClient's EvergreenHUD): a rectangle of your size and colour with an optional border, to frame or back other HUDs. */
public class CustomShapeHudElement extends HudElement {
    public CustomShapeHudElement() {
        width = 100;
        height = 40;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        width = option("Width") instanceof SliderOption w ? w.getIntValue() : 100;
        height = option("Height") instanceof SliderOption h ? h.getIntValue() : 40;
    }

    @Override
    public void render(LadsGraphics g) {
        int fill = option("Fill Color") instanceof ColorOption f ? color(f) : 0x80000000;
        int border = option("Border Color") instanceof ColorOption b ? color(b) : 0xFFFFFFFF;
        int t = option("Border Thickness") instanceof SliderOption s ? Math.min(s.getIntValue(), Math.min(width, height) / 2) : 1;
        g.fill(x + t, y + t, x + width - t, y + height - t, fill);
        if (t <= 0) return;
        g.fill(x, y, x + width, y + t, border);
        g.fill(x, y + height - t, x + width, y + height, border);
        g.fill(x, y + t, x + t, y + height - t, border);
        g.fill(x + width - t, y + t, x + width, y + height - t, border);
    }

    private static int color(ColorOption option) {
        return com.thelads.core.client.PvpVisuals.argb(option);
    }
}
