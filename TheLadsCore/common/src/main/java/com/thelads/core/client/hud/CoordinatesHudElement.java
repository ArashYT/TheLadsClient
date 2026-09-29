package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CoordinatesHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private boolean hasPlayer, vertical;
    private String px, py, pz, prefix, separator;
    public CoordinatesHudElement() {
        this.x = 5;
        this.y = 25;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        hasPlayer = g.getGame().hasPlayer();
        if (!hasPlayer) {
            width = 110; height = Math.max(16, g.fontHeight() + 6);
            return;
        }

        int posX = g.getGame().getPlayerX();
        int posY = g.getGame().getPlayerY();
        int posZ = g.getGame().getPlayerZ();

        int format = optCycle("Format", 2); // 0 = X Y Z, 1 = Coords: X, Y, Z, 2 = Labeled
        vertical = optBool("Vertical", true);
        px = (format == 2 ? "X: " : "") + posX;
        py = (format == 2 ? "Y: " : "") + posY;
        pz = (format == 2 ? "Z: " : "") + posZ;

        if (vertical) {
            int lineHeight = g.fontHeight() + 2;
            this.width = Math.max(70, Math.max(g.textWidth(px), Math.max(g.textWidth(py), g.textWidth(pz))) + 8);
            this.height = 3 * lineHeight + 5;
            return;
        }

        prefix = format == 1 ? "Coords: " : "";
        separator = format == 1 ? ", " : " ";
        this.width = Math.max(120, g.textWidth(prefix) + g.textWidth(px + separator)
                + g.textWidth(py + separator) + g.textWidth(pz) + 8);
        this.height = Math.max(16, g.fontHeight() + 6);
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;
        drawBackground(g);
        if (!hasPlayer) return;
        boolean perAxis = optBool("Per-axis colors", false);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int base = resolveColor();
        int cx = perAxis ? optColor("X Color", base) : base;
        int cy = perAxis ? optColor("Y Color", base) : base;
        int cz = perAxis ? optColor("Z Color", base) : base;
        if (vertical) {
            int lineHeight = g.fontHeight() + 2;
            g.drawText(px, x + (width - g.textWidth(px)) / 2, y + 3, cx, shadow);
            g.drawText(py, x + (width - g.textWidth(py)) / 2, y + 3 + lineHeight, cy, shadow);
            g.drawText(pz, x + (width - g.textWidth(pz)) / 2, y + 3 + 2 * lineHeight, cz, shadow);
            return;
        }
        int tx = x + (width - g.textWidth(prefix + px + separator + py + separator + pz)) / 2;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        if (!prefix.isEmpty()) tx = draw(g, prefix, tx, ty, base, shadow);
        tx = draw(g, px + separator, tx, ty, cx, shadow);
        tx = draw(g, py + separator, tx, ty, cy, shadow);
        draw(g, pz, tx, ty, cz, shadow);
    }

    private int draw(LadsGraphics g, String s, int tx, int ty, int color, boolean shadow) {
        g.drawText(s, tx, ty, color, shadow);
        return tx + g.textWidth(s);
    }
}
