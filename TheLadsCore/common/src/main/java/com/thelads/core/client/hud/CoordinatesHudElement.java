package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

public class CoordinatesHudElement extends HudElement {
    /** Where the player faces, from yaw 0 (south) round to the west, in 45 degree steps, with the axes that way points along. */
    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};
    private static final int DIRECTION_GAP = 8;
    private LadsGraphics preparedGraphics;
    private boolean hasPlayer, vertical, showBiome;
    private String px, py, pz, prefix, separator, biomeText, compass, axes, directionText;
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
        compass = axes = directionText = null;
        if (hasPlayer && optBool("Show Direction", true)) {
            int facing = facing(g.getGame().getYaw(), g.getGame().getPlayerDirection());
            if (facing >= 0) {
                compass = COMPASS[facing];
                axes = AXES[facing];
            }
        }
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
            if (compass != null) maxTextW += DIRECTION_GAP + Math.max(g.textWidth(compass), g.textWidth(axes));
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
        if (compass != null) {
            directionText = separator + compass + " (" + axes + ")";
            horizW += g.textWidth(directionText);
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
            if (compass != null) { // on the right, level with the first two lines
                g.drawText(compass, x + width - 4 - g.textWidth(compass), y + 3, base, shadow);
                g.drawText(axes, x + width - 4 - g.textWidth(axes), y + 3 + lineHeight, base, shadow);
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
            tx = draw(g, separator + "(" + biomeText + ")", tx, ty, base, shadow);
        }
        if (directionText != null) draw(g, directionText, tx, ty, base, shadow);
    }

    /** Which of the eight directions the player faces (an index into COMPASS), by yaw (0 = south), else by the facing's name; -1 when neither says. */
    static int facing(float yaw, String name) {
        if (Float.isFinite(yaw) && yaw >= 0) return (int) ((yaw % 360 + 22.5f) / 45) % 8;
        String facing = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return facing.startsWith("south") ? 0 : facing.startsWith("west") ? 2 : facing.startsWith("north") ? 4 : facing.startsWith("east") ? 6 : -1;
    }

    private int draw(LadsGraphics g, String s, int tx, int ty, int color, boolean shadow) {
        g.drawText(s, tx, ty, color, shadow);
        return tx + g.textWidth(s);
    }
}
