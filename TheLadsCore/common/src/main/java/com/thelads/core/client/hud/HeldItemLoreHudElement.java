package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;

import java.util.List;

/**
 * Small HUD widget: displays custom lore or enchantments of the currently held item.
 * Visual-only.
 */
public class HeldItemLoreHudElement extends TextHudElement {
    public static final String MODULE_NAME = "HeldItemLore";

    public HeldItemLoreHudElement() {
        super(80);
        useOrganizedDefaults();
    }

    @Override
    protected String updateText(LadsGraphics graphics) {
        LadsGameBridge bridge = LadsGameBridge.get();
        List<String> lore = bridge != null ? bridge.getHeldItemLore() : null;
        if (lore != null && !lore.isEmpty()) {
            return String.join("\n", lore);
        }
        return editor ? "Sharpness V\nUnbreaking III" : "";
    }
}
