package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Block Above (OneClient's EvergreenHUD): the first block over your head and how much air is between (HudExtras.above*). */
public class BlockAboveHudElement extends TextHudElement {
    private String text = "", name;
    private int gap = -1, options = -1;

    public BlockAboveHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String n = editor && HudExtras.aboveName.isEmpty() ? "Oak Leaves" : HudExtras.aboveName;
        int a = editor && HudExtras.aboveName.isEmpty() ? 2 : HudExtras.aboveGap, o = optBool("Show Distance", true) ? 1 : 0;
        if (!n.equals(name) || a != gap || o != options) {
            text = n.isEmpty() ? "Above: open sky" : o == 1 ? "Above: " + n + " (" + a + ")" : "Above: " + n;
            name = n;
            gap = a;
            options = o;
        }
        return text;
    }
}
