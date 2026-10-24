package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Held Item Lore (OneClient's EvergreenHUD Item Lore): the enchantments and lore lines of the item in your hand (HudExtras.loreText). */
public class HeldItemLoreHudElement extends TextHudElement {
    public HeldItemLoreHudElement() {
        super(60);
    }

    @Override
    protected String updateText(LadsGraphics g) {
        String lore = HudExtras.loreText;
        return lore.isEmpty() && editor ? "Sharpness V\nUnbreaking III" : lore;
    }
}
