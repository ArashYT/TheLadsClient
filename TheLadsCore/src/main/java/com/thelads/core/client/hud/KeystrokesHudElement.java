package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Animated keystroke overlay: W / A S D, LMB | RMB (with live CPS) and a space
 * bar. Pressed keys light up. Uses the player's actual movement/attack/use
 * keybinds, so it follows custom key rebinds automatically.
 */
public class KeystrokesHudElement extends HudElement {
    private static final int CELL = 18;
    private static final int GAP = 3;

    public KeystrokesHudElement() {
        this.x = 5;
        this.y = 5;
        this.width = 3 * CELL + 2 * GAP;            // 58
        this.height = CELL + GAP + CELL + GAP + (CELL + 8) + GAP + 8; // ~76
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        boolean shadow = HudSettings.getInstance().isTextShadow();
        boolean showCps = optBool("Show CPS", true);
        boolean showSpace = optBool("Show space bar", true);
        int x = this.x;
        int y = this.y;
        int half = (this.width - GAP) / 2;
        int mouseH = showCps ? CELL + 8 : CELL;

        // keep the editor bounding box in sync with the visible rows
        this.height = CELL + GAP + CELL + GAP + mouseH + (showSpace ? GAP + 8 : 0);

        // Row 1: W (middle column)
        drawKey(g, mc, shadow, mc.options.keyUp.isDown(), x + CELL + GAP, y, CELL, CELL, "W", null);

        // Row 2: A S D
        int r2 = y + CELL + GAP;
        drawKey(g, mc, shadow, mc.options.keyLeft.isDown(), x, r2, CELL, CELL, "A", null);
        drawKey(g, mc, shadow, mc.options.keyDown.isDown(), x + CELL + GAP, r2, CELL, CELL, "S", null);
        drawKey(g, mc, shadow, mc.options.keyRight.isDown(), x + 2 * (CELL + GAP), r2, CELL, CELL, "D", null);

        // Row 3: LMB | RMB (with CPS when enabled)
        int r3 = r2 + CELL + GAP;
        String lSub = showCps ? CpsTracker.get().leftCps() + " CPS" : null;
        String rSub = showCps ? CpsTracker.get().rightCps() + " CPS" : null;
        drawKey(g, mc, shadow, mc.options.keyAttack.isDown(), x, r3, half, mouseH, "LMB", lSub);
        drawKey(g, mc, shadow, mc.options.keyUse.isDown(), x + half + GAP, r3, half, mouseH, "RMB", rSub);

        // Row 4: space bar
        if (showSpace) {
            int r4 = r3 + mouseH + GAP;
            drawKey(g, mc, shadow, mc.options.keyJump.isDown(), x, r4, this.width, 8, null, null);
        }
    }

    private void drawKey(GuiGraphicsExtractor g, Minecraft mc, boolean shadow, boolean pressed,
                         int kx, int ky, int kw, int kh, String label, String sub) {
        int bg = pressed ? 0xCCFFFFFF : 0x80000000;
        g.fill(kx, ky, kx + kw, ky + kh, bg);
        int textColor = pressed ? 0xFF000000 : resolveColor();
        if (label != null) {
            int tx = kx + kw / 2 - mc.font.width(label) / 2;
            int ty = (sub != null) ? ky + 3 : ky + (kh - mc.font.lineHeight) / 2 + 1;
            g.text(mc.font, label, tx, ty, textColor, shadow);
        }
        if (sub != null) {
            // CPS text rendered 40% smaller than the key labels.
            float ss = 0.6f;
            int scx = kx + kw / 2;
            int scy = ky + kh - 6;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(scx, scy);
            pose.scale(ss, ss);
            g.text(mc.font, sub, -mc.font.width(sub) / 2, -4, textColor, shadow);
            pose.popMatrix();
        }
    }
}
