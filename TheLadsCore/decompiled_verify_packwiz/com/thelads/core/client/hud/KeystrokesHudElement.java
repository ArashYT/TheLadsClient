/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public class KeystrokesHudElement
extends HudElement {
    private static final int CELL = 18;
    private static final int GAP = 3;

    public KeystrokesHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 60;
        this.height = 79;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        boolean shadow = HudSettings.getInstance().isTextShadow();
        boolean showCps = this.optBool("Show CPS", true);
        boolean showSpace = this.optBool("Show space bar", true);
        int x = this.x;
        int y = this.y;
        int half = (this.width - 3) / 2;
        int mouseH = showCps ? 26 : 18;
        this.height = 42 + mouseH + (showSpace ? 11 : 0);
        this.drawKey(g, mc, shadow, mc.options.keyUp.isDown(), x + 18 + 3, y, 18, 18, "W", null);
        int r2 = y + 18 + 3;
        this.drawKey(g, mc, shadow, mc.options.keyLeft.isDown(), x, r2, 18, 18, "A", null);
        this.drawKey(g, mc, shadow, mc.options.keyDown.isDown(), x + 18 + 3, r2, 18, 18, "S", null);
        this.drawKey(g, mc, shadow, mc.options.keyRight.isDown(), x + 42, r2, 18, 18, "D", null);
        int r3 = r2 + 18 + 3;
        String lSub = showCps ? CpsTracker.get().leftCps() + " CPS" : null;
        String rSub = showCps ? CpsTracker.get().rightCps() + " CPS" : null;
        this.drawKey(g, mc, shadow, mc.options.keyAttack.isDown(), x, r3, half, mouseH, "LMB", lSub);
        this.drawKey(g, mc, shadow, mc.options.keyUse.isDown(), x + half + 3, r3, half, mouseH, "RMB", rSub);
        if (showSpace) {
            int r4 = r3 + mouseH + 3;
            this.drawKey(g, mc, shadow, mc.options.keyJump.isDown(), x, r4, this.width, 8, null, null);
        }
    }

    private void drawKey(GuiGraphicsExtractor g, Minecraft mc, boolean shadow, boolean pressed, int kx, int ky, int kw, int kh, String label, String sub) {
        int textColor;
        int bg = pressed ? -855638017 : Integer.MIN_VALUE;
        g.fill(kx, ky, kx + kw, ky + kh, bg);
        int n = textColor = pressed ? -16777216 : this.resolveColor();
        if (label != null) {
            int n2;
            int tx = kx + kw / 2 - mc.font.width(label) / 2;
            if (sub != null) {
                n2 = ky + 3;
            } else {
                Objects.requireNonNull(mc.font);
                n2 = ky + (kh - 9) / 2 + 1;
            }
            int ty = n2;
            g.text(mc.font, label, tx, ty, textColor, shadow);
        }
        if (sub != null) {
            float ss = 0.6f;
            int scx = kx + kw / 2;
            int scy = ky + kh - 6;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(scx, scy);
            pose.scale(ss, ss);
            g.text(mc.font, sub, -mc.font.width(sub) / 2, -4, textColor, shadow);
            pose.popMatrix();
        }
    }
}

