package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public class CoordinatesHudElement extends HudElement {
    public CoordinatesHudElement() {
        this.x = 5;
        this.y = 25;
        this.width = 110;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            this.height = 16;
            drawBackground(g);
            return;
        }

        BlockPos pos = mc.player.blockPosition();
        int format = optCycle("Format", 0);          // 0 = X Y Z, 1 = Coords: X, Y, Z, 2 = Labeled
        boolean vertical = optBool("Vertical", false);
        boolean perAxis = optBool("Per-axis colors", false);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int base = resolveColor();
        int cx = perAxis ? optColor("X Color", base) : base;
        int cy = perAxis ? optColor("Y Color", base) : base;
        int cz = perAxis ? optColor("Z Color", base) : base;

        String px = (format == 2 ? "X: " : "") + pos.getX();
        String py = (format == 2 ? "Y: " : "") + pos.getY();
        String pz = (format == 2 ? "Z: " : "") + pos.getZ();

        if (vertical) {
            this.width = 70;
            this.height = 3 * 11 + 5;
            drawBackground(g);
            g.text(mc.font, px, x + 4, y + 3, cx, shadow);
            g.text(mc.font, py, x + 4, y + 3 + 11, cy, shadow);
            g.text(mc.font, pz, x + 4, y + 3 + 22, cz, shadow);
            return;
        }

        this.width = 120;
        this.height = 16;
        drawBackground(g);
        int tx = x + 4;
        int ty = y + (height - mc.font.lineHeight) / 2 + 1;
        if (format == 1) {
            tx = draw(g, mc, "Coords: ", tx, ty, base, shadow);
            tx = draw(g, mc, px + ", ", tx, ty, cx, shadow);
            tx = draw(g, mc, py + ", ", tx, ty, cy, shadow);
            draw(g, mc, pz, tx, ty, cz, shadow);
        } else {
            tx = draw(g, mc, px + " ", tx, ty, cx, shadow);
            tx = draw(g, mc, py + " ", tx, ty, cy, shadow);
            draw(g, mc, pz, tx, ty, cz, shadow);
        }
    }

    private int draw(GuiGraphicsExtractor g, Minecraft mc, String s, int tx, int ty, int color, boolean shadow) {
        g.text(mc.font, s, tx, ty, color, shadow);
        return tx + mc.font.width(s);
    }
}
