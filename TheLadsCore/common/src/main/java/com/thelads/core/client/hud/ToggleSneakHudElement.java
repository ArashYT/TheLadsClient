package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleSneakModule;

public class ToggleSneakHudElement extends TextHudElement {
    public ToggleSneakHudElement() {
        super(70);
        this.x = 5;
        this.y = 208;
        this.width = 85;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        var mod = ModuleManager.getInstance().getModule("ToggleSneak");
        boolean isToggled = mod instanceof ToggleSneakModule sneak && sneak.isToggled();
        boolean hasPlayer = g.getGame() != null && g.getGame().hasPlayer();
        if (!hasPlayer) {
            return "[Sneaking (Toggled)]";
        }
        if (isToggled) {
            return "[Sneaking (Toggled)]";
        }
        return "";
    }
}
