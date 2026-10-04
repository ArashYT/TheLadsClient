package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleSprintModule;

/** Toggle Sprint &amp; Sneak's one HUD line: "[Sprinting (Toggled)] [Sneaking (Key Held)]" and the like (ToggleSprintModule.status). */
public class ToggleSprintHudElement extends TextHudElement {
    private static final String SAMPLE = "[Sprinting (Toggled)]";

    public ToggleSprintHudElement() {
        super(70);
        this.x = 5;
        this.y = 190;
        this.width = 85;
        this.height = 16;
    }

    @Override
    protected String updateText(LadsGraphics g) {
        boolean hasPlayer = g.getGame() != null && g.getGame().hasPlayer();
        String status = hasPlayer && ModuleManager.getInstance().getModule(ToggleSprintModule.NAME) instanceof ToggleSprintModule module
            ? module.status() : "";
        return status.isEmpty() && (editor || !hasPlayer) ? SAMPLE : status;
    }
}
