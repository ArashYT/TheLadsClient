package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleSprintModule;

public class ToggleSprintHudElement extends TextHudElement {
    public ToggleSprintHudElement() {
        super(70);
        this.x = 5;
        this.y = 190;
        this.width = 85;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        var mod = ModuleManager.getInstance().getModule("ToggleSprint");
        boolean isToggled = mod instanceof ToggleSprintModule sprint && sprint.isToggled();
        boolean hasPlayer = g.getGame() != null && g.getGame().hasPlayer();
        if (!hasPlayer) {
            return "[Sprinting (Toggled)]";
        }
        if (isToggled) {
            return "[Sprinting (Toggled)]";
        }
        return "";
    }
}
