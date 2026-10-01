package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CoordinatesHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private boolean hasPlayer, vertical, showBiome;
    private String px, py, pz, prefix, separator, biomeText;
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
        showBiome = optBool("Show Biome", false);
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

        String bName = g.getGame().getBiomeName();
        biomeText = (bName != null && !bName.isEmpty()) ? bName : "Plains";

        if (vertical) {
            int lineHeight = g.fontHeight() + 2;
            int maxTextW = Math.max(g.textWidth(px), Math.max(g.textWidth(py), g.textWidth(pz)));
            if (showBiome) {
                maxTextW = Math.max(maxTextW, g.textWidth("Biome: " + biomeText));
            }
            this.width = Math.max(70, maxTextW + 8);
            this.height = (showBiome ? 4 : 3) * lineHeight + 5;
            return;
        }

        prefix = format == 1 ? "Coords: " : "";
        separator = format == 1 ? ", " : " ";
        int horizW = g.textWidth(prefix) + g.textWidth(px + separator)
                + g.textWidth(py + separator) + g.textWidth(pz);
        if (showBiome) {
            horizW += g.textWidth(separator + "(" + biomeText + ")");
        }
        this.width = Math.max(120, horizW + 8);
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
            // Force left-aligned
            g.drawText(px, x + 4, y + 3, cx, shadow);
            g.drawText(py, x + 4, y + 3 + lineHeight, cy, shadow);
            g.drawText(pz, x + 4, y + 3 + 2 * lineHeight, cz, shadow);
            if (showBiome) {
                g.drawText("Biome: " + biomeText, x + 4, y + 3 + 3 * lineHeight, base, shadow);
            }
            return;
        }
        // Force left-aligned
        int tx = x + 4;
        int ty = y + (height - g.fontHeight()) / 2 + 1;
        if (!prefix.isEmpty()) tx = draw(g, prefix, tx, ty, base, shadow);
        tx = draw(g, px + separator, tx, ty, cx, shadow);
        tx = draw(g, py + separator, tx, ty, cy, shadow);
        tx = draw(g, pz, tx, ty, cz, shadow);
        if (showBiome) {
            draw(g, separator + "(" + biomeText + ")", tx, ty, base, shadow);
        }
    }

    private int draw(LadsGraphics g, String s, int tx, int ty, int color, boolean shadow) {
        g.drawText(s, tx, ty, color, shadow);
        return tx + g.textWidth(s);
    }
}
