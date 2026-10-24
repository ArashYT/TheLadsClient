package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;

/** Player Head (OneClient's EvergreenHUD): your skin's face, with its hat layer. */
public class PlayerHeadHudElement extends HudElement {
    public PlayerHeadHudElement() {
        width = 24;
        height = 24;
    }

    @Override
    public void render(LadsGraphics g) {
        var game = g.getGame();
        String name = game == null ? "" : game.getPlayerName();
        drawBackground(g);
        g.drawHead(name == null || name.isEmpty() ? "Steve" : name, null, x + 4, y + 4, 16);
    }
}
