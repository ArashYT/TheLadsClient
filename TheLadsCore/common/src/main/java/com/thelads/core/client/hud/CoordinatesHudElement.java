package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CoordinatesHudElement extends HudElement {
    public CoordinatesHudElement() {
        this.x = 5;
        this.y = 25;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void render(LadsGraphics g) {
        if (!g.getGame().hasPlayer()) {
            this.height = 16;
            drawBackground(g);
            return;
        }

        int posX = g.getGame().getPlayerX();
        int posY = g.getGame().getPlayerY();
        int posZ = g.getGame().getPlayerZ();

        int format = optCycle("Format", 0); // 0 = X Y Z, 1 = Coords: X, Y, Z, 2 = Labeled
        boolean vertical = optBool("Vertical", false);
        boolean perAxis = optBool("Per-axis colors", false);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int base = resolveColor();
        int cx = perAxis ? optColor("X Color", base) : base;
        int cy = perAxis ? optColor("Y Color", base) : base;
        int cz = perAxis ? optColor("Z Color", base) : base;

        String px = (format == 2 ? "X: " : "") + posX;
        String py = (format == 2 ? "Y: " : "") + posY;
        String pz = (format == 2 ? "Z: " : "") + posZ;

        if (vertical) {
            this.width = 70;
            this.height = 3 * 11 + 5;
            drawBackground(g);
            g.drawText(px, x + 4, y + 3, cx, shadow);
            g.drawText(py, x + 4, y + 3 + 11, cy, shadow);
            g.drawText(pz, x + 4, y + 3 + 22, cz, shadow);
            return;
        }

        this.width = 120;
        this.height = 16;
        drawBackground(g);
        int tx = x + 4;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        if (format == 1) {
            tx = draw(g, "Coords: ", tx, ty, base, shadow);
            tx = draw(g, px + ", ", tx, ty, cx, shadow);
            tx = draw(g, py + ", ", tx, ty, cy, shadow);
            draw(g, pz, tx, ty, cz, shadow);
        } else {
            tx = draw(g, px + " ", tx, ty, cx, shadow);
            tx = draw(g, py + " ", tx, ty, cy, shadow);
            draw(g, pz, tx, ty, cz, shadow);
        }
    }

    private int draw(LadsGraphics g, String s, int tx, int ty, int color, boolean shadow) {
        g.drawText(s, tx, ty, color, shadow);
        return tx + g.textWidth(s);
    }
}
