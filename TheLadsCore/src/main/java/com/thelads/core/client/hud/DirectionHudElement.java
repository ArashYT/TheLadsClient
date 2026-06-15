package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DirectionHudElement extends HudElement {
    private static final String[] CARDINAL_SHORT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] CARDINAL_LONG  = {"South", "Southwest", "West", "Northwest",
                                                     "North", "Northeast", "East", "Southeast"};

    public DirectionHudElement() {
        this.x = 5;
        this.y = 125;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float yaw = mc.player.getYRot();
        yaw = ((yaw % 360.0f) + 360.0f) % 360.0f;
        int idx = (int) Math.round(yaw / 45.0) % 8;

        int format   = optCycle("Format", 0);    // 0=Cardinal, 1=Degrees, 2=Both
        boolean longName = optBool("Long names", false);
        String[] cardinals = longName ? CARDINAL_LONG : CARDINAL_SHORT;

        String text;
        int degrees = Math.round(yaw);
        if (format == 0) {
            text = cardinals[idx];
        } else if (format == 1) {
            text = degrees + "°";
        } else {
            text = cardinals[idx] + " (" + degrees + "°)";
        }
        drawCenteredText(g, text);
    }
}
