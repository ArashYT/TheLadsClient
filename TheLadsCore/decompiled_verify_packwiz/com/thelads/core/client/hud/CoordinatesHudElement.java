/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public class CoordinatesHudElement
extends HudElement {
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
            this.drawBackground(g);
            return;
        }
        BlockPos pos = mc.player.blockPosition();
        int format = this.optCycle("Format", 0);
        boolean vertical = this.optBool("Vertical", false);
        boolean perAxis = this.optBool("Per-axis colors", false);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        int base = this.resolveColor();
        int cx = perAxis ? this.optColor("X Color", base) : base;
        int cy = perAxis ? this.optColor("Y Color", base) : base;
        int cz = perAxis ? this.optColor("Z Color", base) : base;
        String px = (format == 2 ? "X: " : "") + pos.getX();
        String py = (format == 2 ? "Y: " : "") + pos.getY();
        String pz = (format == 2 ? "Z: " : "") + pos.getZ();
        if (vertical) {
            this.width = 70;
            this.height = 38;
            this.drawBackground(g);
            g.text(mc.font, px, this.x + 4, this.y + 3, cx, shadow);
            g.text(mc.font, py, this.x + 4, this.y + 3 + 11, cy, shadow);
            g.text(mc.font, pz, this.x + 4, this.y + 3 + 22, cz, shadow);
            return;
        }
        this.width = 120;
        this.height = 16;
        this.drawBackground(g);
        int tx = this.x + 4;
        Objects.requireNonNull(mc.font);
        int ty = this.y + (this.height - 9) / 2 + 1;
        if (format == 1) {
            tx = this.draw(g, mc, "Coords: ", tx, ty, base, shadow);
            tx = this.draw(g, mc, px + ", ", tx, ty, cx, shadow);
            tx = this.draw(g, mc, py + ", ", tx, ty, cy, shadow);
            this.draw(g, mc, pz, tx, ty, cz, shadow);
        } else {
            tx = this.draw(g, mc, px + " ", tx, ty, cx, shadow);
            tx = this.draw(g, mc, py + " ", tx, ty, cy, shadow);
            this.draw(g, mc, pz, tx, ty, cz, shadow);
        }
    }

    private int draw(GuiGraphicsExtractor g, Minecraft mc, String s, int tx, int ty, int color, boolean shadow) {
        g.text(mc.font, s, tx, ty, color, shadow);
        return tx + mc.font.width(s);
    }
}

