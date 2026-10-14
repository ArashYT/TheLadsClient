package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;

public class KeystrokesHudElement extends HudElement {
    private static final int GAP = 2, SPACE = 6;
    private LadsGraphics preparedGraphics;
    private boolean showCps, showSpace;
    private int lastLeftCps = -1, lastRightCps = -1;
    private String leftLabel = "0 CPS", rightLabel = "0 CPS";
    private int keyWidth, keyHeight, cpsHeight, cpsY, spaceY;
    public KeystrokesHudElement() {
        this.x = 10;
        this.y = 100;
        this.width = 64;
        this.height = 76;
    }

    /**
     * The keys fill the element edge to edge, so its bounds are the drawn keys' outer edges (A and D, LMB and RMB and the space bar
     * all sit on them). Three equal keys and two gaps make the width 12n + 4 and the rows add up to a multiple of 4 high too, so
     * the bounds stay whole pixels at every Size (50 to 200 in steps of 25).
     */
    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        showCps = optBool("Show CPS", true);
        showSpace = optBool("Show space bar", true);
        int leftCps = CpsTracker.get().leftCps();
        if (leftCps != lastLeftCps) {
            lastLeftCps = leftCps;
            leftLabel = leftCps + " CPS";
        }
        int rightCps = CpsTracker.get().rightCps();
        if (rightCps != lastRightCps) {
            lastRightCps = rightCps;
            rightLabel = rightCps + " CPS";
        }
        int cpsWidth = Math.max(31, (int)Math.ceil(Math.max(g.textWidth(leftLabel), g.textWidth(rightLabel)) * .65) + 4);
        keyWidth = (Math.max(64, showCps ? 2 * cpsWidth + GAP : 0) - 2 * GAP + 11) / 12 * 4;
        width = 3 * keyWidth + 2 * GAP;
        keyHeight = Math.max(19, g.fontHeight() + 7) | 1;
        cpsHeight = Math.max(26, 2 * g.fontHeight() + 8);
        cpsHeight += Math.floorMod(2 - cpsHeight, 4);
        cpsY = 2 * keyHeight + 2 * GAP;
        spaceY = showCps ? cpsY + cpsHeight + GAP : cpsY;
        height = showSpace ? spaceY + SPACE : showCps ? cpsY + cpsHeight : cpsY - GAP;
    }

    @Override public void render(LadsGraphics g) {
        if (preparedGraphics != g) prepareRender(g, false);
        preparedGraphics = null;

        int baseBg = resolveBackground();
        int pressedBg = 0x80FFFFFF; // press flash stays visible even with no background plate
        int color = resolveColor();
        int middle = x + (width - keyWidth) / 2, lower = y + keyHeight + GAP, leftWidth = (width - GAP) / 2; // width: a docked group may have widened it

        drawKey(g, "W", middle, y, keyWidth, keyHeight, g.getGame().isKeyDown("W"), baseBg, pressedBg, color);
        drawKey(g, "A", x, lower, keyWidth, keyHeight, g.getGame().isKeyDown("A"), baseBg, pressedBg, color);
        drawKey(g, "S", middle, lower, keyWidth, keyHeight, g.getGame().isKeyDown("S"), baseBg, pressedBg, color);
        drawKey(g, "D", x + width - keyWidth, lower, keyWidth, keyHeight, g.getGame().isKeyDown("D"), baseBg, pressedBg, color);

        if (showCps) {
            drawMouseKey(g, "LMB", leftLabel, x, y + cpsY, leftWidth, cpsHeight, g.getGame().isKeyDown("LMB"), baseBg, pressedBg, color);
            drawMouseKey(g, "RMB", rightLabel, x + leftWidth + GAP, y + cpsY, width - leftWidth - GAP, cpsHeight, g.getGame().isKeyDown("RMB"), baseBg, pressedBg, color);
        }

        if (showSpace) {
            int sy = y + spaceY;
            boolean down = g.getGame().isKeyDown("Space");
            g.fill(x, sy, x + width, sy + SPACE, down ? pressedBg : baseBg);
            g.fill(x + width / 2 - 10, sy + SPACE / 2, x + width / 2 + 10, sy + SPACE / 2 + 1, down && (pressedBg >>> 24) != 0 ? 0xFF000000 : color);
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
