package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Small HUD widget: displays count of loaded/visible entities.
 * Visual-only.
 */
public class EntityCounterHudElement extends TextHudElement {
    public static final String MODULE_NAME = "EntityCounter";

    public EntityCounterHudElement() {
        super(70);
        useOrganizedDefaults();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        int count = bridge != null ? bridge.getLoadedEntityCount() : 0;
        if (count == 0 && editor) count = 28;
        return "Entities: " + count;
    }
}
