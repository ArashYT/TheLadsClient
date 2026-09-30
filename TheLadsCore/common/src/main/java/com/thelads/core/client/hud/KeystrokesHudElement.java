package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class KeystrokesHudElement extends HudElement {
    private LadsGraphics preparedGraphics;
    private boolean showCps, showSpace;
    private String leftLabel, rightLabel;
    private int keyWidth, keyHeight, cpsWidth, cpsHeight, cpsY, spaceY;
    public KeystrokesHudElement() {
        this.x = 10;
        this.y = 100;
        this.width = 68;
        this.height = 70;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        showCps = optBool("Show CPS", true);
        showSpace = optBool("Show space bar", true);
        leftLabel = CpsTracker.get().leftCps() + " CPS";
        rightLabel = CpsTracker.get().rightCps() + " CPS";
        cpsWidth = Math.max(31, (int)Math.ceil(Math.max(g.textWidth(leftLabel), g.textWidth(rightLabel)) * .65) + 4);
        width = showCps ? Math.max(68, cpsWidth * 2 + 6) : 68;
        keyWidth = (width - 8) / 3;
        keyHeight = Math.max(18, g.fontHeight() + 6);
        cpsHeight = Math.max(26, 2 * g.fontHeight() + 8);
        cpsY = 2 + 2 * (keyHeight + 2);
        spaceY = cpsY + (showCps ? cpsHeight + 2 : 0);
        height = showSpace ? spaceY + 10 : showCps ? cpsY + cpsHeight + 2 : cpsY;
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;

        int baseBg = resolveBackground();
        int pressedBg = com.thelads.core.config.HudSettings.getInstance().isBackgrounds() ? 0x80FFFFFF : 0;
        int color = resolveColor();

        // W key
        drawKey(g, "W", x + (width - keyWidth) / 2, y + 2, keyWidth, keyHeight, g.getGame().isKeyDown("W"), baseBg, pressedBg, color);
        // A key
        drawKey(g, "A", x + 2, y + keyHeight + 4, keyWidth, keyHeight, g.getGame().isKeyDown("A"), baseBg, pressedBg, color);
        // S key
        drawKey(g, "S", x + (width - keyWidth) / 2, y + keyHeight + 4, keyWidth, keyHeight, g.getGame().isKeyDown("S"), baseBg, pressedBg, color);
        // D key
        drawKey(g, "D", x + width - keyWidth - 2, y + keyHeight + 4, keyWidth, keyHeight, g.getGame().isKeyDown("D"), baseBg, pressedBg, color);

        if (showCps) {
            drawMouseKey(g, "LMB", leftLabel, x + 2, y + cpsY, cpsWidth, cpsHeight, g.getGame().isKeyDown("LMB"), baseBg, pressedBg, color);
            drawMouseKey(g, "RMB", rightLabel, x + width - cpsWidth - 2, y + cpsY, cpsWidth, cpsHeight, g.getGame().isKeyDown("RMB"), baseBg, pressedBg, color);
        }

        if (showSpace) {
            int sy = y + spaceY;
            boolean down = g.getGame().isKeyDown("Space");
            g.fill(x + 2, sy, x + width - 2, sy + 8, down ? pressedBg : baseBg);
            g.fill(x + width / 2 - 10, sy + 4, x + width / 2 + 10, sy + 5, down && (pressedBg >>> 24) != 0 ? 0xFF000000 : color);
        }
    }

    private void drawMouseKey(LadsGraphics g, String name, String cps, int kx, int ky, int kw, int kh, boolean down, int bg, int pressedBg, int textCol) {
        g.fill(kx, ky, kx + kw, ky + kh, down ? pressedBg : bg);
        int color = down && (pressedBg >>> 24) != 0 ? 0xFF000000 : textCol;
        g.drawCenteredText(name, kx + kw / 2, ky + 3, color);
        g.pushPose();
        g.translate(kx + kw / 2f, ky + g.fontHeight() + 5);
        g.scale(.65f, .65f);
        g.drawCenteredText(cps, 0, 0, color);
        g.popPose();
    }

    private void drawKey(LadsGraphics g, String label, int kx, int ky, int kw, int kh, boolean down, int bg, int pressedBg, int textCol) {
        g.fill(kx, ky, kx + kw, ky + kh, down ? pressedBg : bg);
        int tw = g.textWidth(label);
        int tx = kx + (kw - tw) / 2;
        int ty = ky + (kh - g.fontHeight()) / 2 + 1;
        g.drawText(label, tx, ty, down && (pressedBg >>> 24) != 0 ? 0xFF000000 : textCol);
    }
}
