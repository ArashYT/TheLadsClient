package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Entity Counter (OneClient's EvergreenHUD): the entities loaded around you, the count F3 shows (HudExtras.entityCount). */
public class EntityCounterHudElement extends TextHudElement {
    private String text = "";
    private int count = -1;

    public EntityCounterHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        int c = editor && HudExtras.entityCount == 0 ? 28 : HudExtras.entityCount;
        if (c != count) {
            text = "Entities: " + c;
            count = c;
        }
        return text;
    }
}
