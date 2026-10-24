package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

/**
 * Small HUD widget: displays the block directly above the player's head.
 * Visual-only.
 */
public class BlockAboveHudElement extends TextHudElement {
    public static final String MODULE_NAME = "BlockAbove";

    public BlockAboveHudElement() {
        super(70);
        useOrganizedDefaults();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        String block = bridge != null ? bridge.getBlockAboveName() : null;
        if (block == null || block.isEmpty()) block = "Air";
        return "Above: " + block;
    }
}
