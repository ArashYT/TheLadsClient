package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class KeystrokesHudElement extends HudElement {
    public KeystrokesHudElement() {
        this.x = 10;
        this.y = 100;
        this.width = 68;
        this.height = 70;
    }

    @Override
    public void render(LadsGraphics g) {
        boolean showCps = optBool("Show CPS", true);
        boolean showSpace = optBool("Show space bar", true);
        this.height = showSpace ? (showCps ? 70 : 60) : (showCps ? 50 : 40);

        int baseBg = resolveBackground();
        int pressedBg = 0x80FFFFFF;
        int color = resolveColor();

        // W key
        drawKey(g, "W", x + 24, y + 2, 20, 18, g.getGame().isKeyDown("W"), baseBg, pressedBg, color);
        // A key
        drawKey(g, "A", x + 2, y + 22, 20, 18, g.getGame().isKeyDown("A"), baseBg, pressedBg, color);
        // S key
        drawKey(g, "S", x + 24, y + 22, 20, 18, g.getGame().isKeyDown("S"), baseBg, pressedBg, color);
        // D key
        drawKey(g, "D", x + 46, y + 22, 20, 18, g.getGame().isKeyDown("D"), baseBg, pressedBg, color);

        if (showCps) {
            String lmbText = "LMB" + (showCps ? " " + CpsTracker.get().leftCps() : "");
            String rmbText = "RMB" + (showCps ? " " + CpsTracker.get().rightCps() : "");
            drawKey(g, lmbText, x + 2, y + 42, 31, 16, g.getGame().isKeyDown("LMB"), baseBg, pressedBg, color);
            drawKey(g, rmbText, x + 35, y + 42, 31, 16, g.getGame().isKeyDown("RMB"), baseBg, pressedBg, color);
        }

        if (showSpace) {
            int sy = showCps ? y + 60 : y + 42;
            drawKey(g, "───", x + 2, sy, 64, 8, g.getGame().isKeyDown("Space"), baseBg, pressedBg, color);
        }
    }

    private void drawKey(LadsGraphics g, String label, int kx, int ky, int kw, int kh, boolean down, int bg, int pressedBg, int textCol) {
        g.fill(kx, ky, kx + kw, ky + kh, down ? pressedBg : bg);
        int tw = g.textWidth(label);
        int tx = kx + (kw - tw) / 2;
        int ty = ky + (kh - g.fontHeight()) / 2 + 1;
        g.drawText(label, tx, ty, down ? 0xFF000000 : textCol);
    }
}
