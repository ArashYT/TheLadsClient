package com.thelads.core.client.hud;

import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.MenuGraphics;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KeystrokesModule;

public class KeystrokesHudElement extends HudElement {
    private static final int GAP = 2, SPACE = 6;
    private static final String[] KEY_NAMES = {"W", "A", "S", "D", "SPACE", "SNK", "SPR", "LMB", "RMB"};
    private static final String[] NUMBERS = new String[256];
    static {
        for (int i = 0; i < 256; i++) {
            NUMBERS[i] = String.valueOf(i);
        }
    }

    private static String getNumberString(int n) {
        if (n >= 0 && n < 256) return NUMBERS[n];
        return "99+";
    }

    private KeystrokesModule cachedModule;
    private long lastModuleModified = -1;

    // Classic state (byte-for-byte unscaled original geometry)
    private LadsGraphics preparedGraphics;
    private boolean showCps, showSpace;
    private int lastLeftCps = -1, lastRightCps = -1;
    private String leftLabel = "0 CPS", rightLabel = "0 CPS";
    private int keyWidth, keyHeight, cpsHeight, cpsY, spaceY;

    // Clean state
    private boolean cleanShowSneakSprint;
    private boolean cleanPressAnim;
    private boolean cleanTickSync;
    private int cleanKeyColor, cleanKeyPressedColor, cleanKeyBg, cleanKeyPressedBg;
    private boolean cleanKeyShadow, cleanKeyPressedShadow, cleanCustomShadow;
    private int cleanShadowColor, cleanPressedShadowColor;
    private boolean cleanRainbowKey, cleanRainbowKeyPressed, cleanRainbowBg, cleanRainbowBgPressed;
    private boolean cleanRainbowShadow, cleanRainbowPressedShadow;
    private double cleanRainbowSpeed = 1.0;

    private final float[] animFactor = new float[9];
    private final boolean[] tickKeyDown = new boolean[9];
    private int lastSampledTick = Integer.MIN_VALUE;
    private long lastFrameTime = 0;

    // Mouse movement tracker
    private static final int MAX_TRAIL = 100;
    private final float[] trailX = new float[MAX_TRAIL];
    private final float[] trailY = new float[MAX_TRAIL];
    private final long[] trailTime = new long[MAX_TRAIL];
    private int trailHead = 0;
    private int trailCount = 0;
    private float lastTrailX = -9999, lastTrailY = -9999;
    private float lastYaw = -9999, lastPitch = -9999;
    private float mouseSmoothX = 0, mouseSmoothY = 0;
    private float mouseDotX = 0, mouseDotY = 0;

    public KeystrokesHudElement() {
        this.x = 10;
        this.y = 100;
        this.width = 67;
        this.height = 95;
    }

    private KeystrokesModule keystrokesModule() {
        if (cachedModule == null && moduleName != null) {
            Module m = ModuleManager.getInstance().getModule(moduleName);
            if (m instanceof KeystrokesModule km) cachedModule = km;
        }
        return cachedModule;
    }

    private boolean isClean() {
        KeystrokesModule km = keystrokesModule();
        if (km != null) return km.isClean();
        return optCycle("Style", KeystrokesModule.STYLE_CLEAN) == KeystrokesModule.STYLE_CLEAN;
    }

    @Override
    public void matchLayoutWidth(int pixels) {
        // Keystrokes button sizes do not change when grouped (no stretching to group width, no shrink)
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        preparedGraphics = g;
        KeystrokesModule km = keystrokesModule();
        lastModuleModified = (km != null) ? km.getLastModified() : 0;
        if (isClean()) {
            prepareRenderClean(g, editor);
        } else {
            prepareRenderClassic(g, editor);
        }
    }

    private void prepareRenderClassic(LadsGraphics g, boolean editor) {
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

    private void prepareRenderClean(LadsGraphics g, boolean editor) {
        KeystrokesModule km = keystrokesModule();
        if (km != null) {
            showCps = km.showCps.get();
            showSpace = km.showSpace.get();
            cleanShowSneakSprint = km.showSneakSprintRow.get();
            cleanPressAnim = km.pressAnimation.get();
            cleanTickSync = km.tickSyncedKeyPresses.get();

            cleanKeyColor = km.keyColor.isUseGlobal() ? resolveColor() : km.keyColor.getColor();
            cleanKeyBg = km.keyBackgroundColor.isUseGlobal() ? resolveBackground() : km.keyBackgroundColor.getColor();
            cleanKeyPressedBg = km.keyPressedBackgroundColor.isUseGlobal() ? 0xAAFFFFFF : km.keyPressedBackgroundColor.getColor();
            cleanKeyPressedColor = km.keyPressedColor.isUseGlobal()
                    ? ((cleanKeyPressedBg >>> 24) != 0 ? 0xFF000000 : resolveColor())
                    : km.keyPressedColor.getColor();

            cleanKeyShadow = km.keyTextShadow.get();
            cleanKeyPressedShadow = km.keyPressedTextShadow.get();
            cleanCustomShadow = km.useCustomTextShadowColor.get();
            cleanShadowColor = km.keyTextShadowColor.getColor();
            cleanPressedShadowColor = km.keyPressedTextShadowColor.getColor();

            cleanRainbowKey = km.rainbowKeyNormal.get();
            cleanRainbowKeyPressed = km.rainbowKeyPressed.get();
            cleanRainbowBg = km.rainbowBackgroundNormal.get();
            cleanRainbowBgPressed = km.rainbowBackgroundPressed.get();
            cleanRainbowShadow = km.rainbowKeyTextShadow.get();
            cleanRainbowPressedShadow = km.rainbowKeyPressedTextShadow.get();
            cleanRainbowSpeed = km.rainbowSpeed.get();
        } else {
            showCps = optBool("Show CPS", true);
            showSpace = optBool("Show space bar", true);
            cleanShowSneakSprint = optBool("Show Sneak/Sprint Row", true);
            cleanPressAnim = optBool("Press Animation", true);
            cleanTickSync = optBool("Tick Synced Key Presses", false);

            cleanKeyColor = resolveColor();
            cleanKeyBg = resolveBackground();
            cleanKeyPressedBg = 0xAAFFFFFF;
            cleanKeyPressedColor = (cleanKeyPressedBg >>> 24) != 0 ? 0xFF000000 : resolveColor();

            cleanKeyShadow = false;
            cleanKeyPressedShadow = false;
            cleanCustomShadow = false;
            cleanShadowColor = 0xFF000000;
            cleanPressedShadowColor = 0xFF000000;

            cleanRainbowKey = false;
            cleanRainbowKeyPressed = false;
            cleanRainbowBg = false;
            cleanRainbowBgPressed = false;
            cleanRainbowShadow = false;
            cleanRainbowPressedShadow = false;
            cleanRainbowSpeed = 1.0;
        }

        width = 67;
        int nextY = 46;
        if (showSpace) nextY += 17;
        if (cleanShowSneakSprint) nextY += 17;
        nextY += 15;
        height = nextY;
    }

    @Override
    public void render(LadsGraphics g) {
        KeystrokesModule km = keystrokesModule();
        long mod = (km != null) ? km.getLastModified() : 0;
        if (preparedGraphics != g || mod != lastModuleModified) {
            prepareRender(g, false);
        }

        if (isClean()) {
            renderClean(g);
        } else {
            renderClassic(g);
        }
    }

    private void renderClassic(LadsGraphics g) {
        int baseBg = resolveBackground();
        int pressedBg = 0x80FFFFFF;
        int color = resolveColor();
        int middle = x + (width - keyWidth) / 2, lower = y + keyHeight + GAP, leftWidth = (width - GAP) / 2;

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

    private void renderClean(LadsGraphics g) {
        long now = System.currentTimeMillis();
        float delta = (lastFrameTime == 0) ? 1.0f : Math.min(3.0f, (now - lastFrameTime) / 50.0f);
        lastFrameTime = now;

        boolean tickSync = cleanTickSync;
        if (tickSync) {
            int currentTick = g.getGame().getClientTicks();
            if (currentTick != lastSampledTick) {
                lastSampledTick = currentTick;
                for (int i = 0; i < 9; i++) {
                    tickKeyDown[i] = g.getGame().isKeyDown(KEY_NAMES[i]);
                }
            }
        }

        // Hoist Math.pow computations out of the 9-key loop
        float pressFactor = cleanPressAnim ? 1.0f - (float) Math.pow(0.3, delta) : 1.0f;
        float releaseFactor = cleanPressAnim ? 1.0f - (float) Math.pow(0.4, delta) : 1.0f;

        for (int i = 0; i < 9; i++) {
            boolean down = tickSync ? tickKeyDown[i] : g.getGame().isKeyDown(KEY_NAMES[i]);
            if (cleanPressAnim) {
                float target = down ? 1.0f : 0.0f;
                float current = animFactor[i];
                float next = current + (target - current) * (down ? pressFactor : releaseFactor);
                if (Math.abs(next - target) < 0.005f) next = target;
                animFactor[i] = next;
            } else {
                animFactor[i] = down ? 1.0f : 0.0f;
            }
        }

        double phase = ((now / 10000.0) * cleanRainbowSpeed) % 1.0;
        int idleText = cleanRainbowKey ? rainbow(phase, 0.0, cleanKeyColor >>> 24) : cleanKeyColor;
        int pressedText = cleanRainbowKeyPressed ? rainbow(phase, 0.5, cleanKeyPressedColor >>> 24) : cleanKeyPressedColor;
        int idleBg = cleanRainbowBg ? rainbow(phase, 0.5, cleanKeyBg >>> 24) : cleanKeyBg;
        int pressedBg = cleanRainbowBgPressed ? rainbow(phase, 0.0, cleanKeyPressedBg >>> 24) : cleanKeyPressedBg;
        int idleShadow = cleanRainbowShadow ? rainbow(phase, 0.25, cleanShadowColor >>> 24) : cleanShadowColor;
        int pressedShadow = cleanRainbowPressedShadow ? rainbow(phase, 0.75, cleanPressedShadowColor >>> 24) : cleanPressedShadowColor;

        // Row 0: W (x + 23, y, 21, 21)
        drawCleanKey(g, "W", x + 23, y, 21, 21, 0, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);

        // Row 1: A, S, D (y + 23)
        int row1Y = y + 23;
        drawCleanKey(g, "A", x, row1Y, 21, 21, 1, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
        drawCleanKey(g, "S", x + 23, row1Y, 21, 21, 2, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
        drawCleanKey(g, "D", x + 46, row1Y, 21, 21, 3, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);

        int curY = y + 46;
        // Row 2: Space (67 x 15) - Centered bar (11x2 px) instead of text glyph
        if (showSpace) {
            drawCleanSpaceBar(g, x, curY, 67, 15, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
            curY += 17;
        }

        // Row 3: Sneak & Sprint
        if (cleanShowSneakSprint) {
            drawCleanKey(g, "SNK", x, curY, 29, 15, 5, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
            drawCleanKey(g, "SPR", x + 37, curY, 30, 15, 6, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
            curY += 17;
        }

        // Row 4: Mouse Row (LMB, Center Tracker, RMB)
        String lmbLabel = showCps ? getNumberString(CpsTracker.get().leftCps()) : "LMB";
        String rmbLabel = showCps ? getNumberString(CpsTracker.get().rightCps()) : "RMB";

        // LMB (x, curY, 21, 15)
        drawCleanMousePill(g, lmbLabel, x, curY, 21, 15, 7, true, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);

        // Center Mouse Tracker (x + 21, curY, 25, 15)
        renderMouseTracker(g, x + 21, curY, 25, 15, idleBg, idleText, delta, now);

        // RMB (x + 46, curY, 21, 15)
        drawCleanMousePill(g, rmbLabel, x + 46, curY, 21, 15, 8, false, idleBg, pressedBg, idleText, pressedText, idleShadow, pressedShadow);
    }

    private void drawCleanKey(LadsGraphics g, String label, int kx, int ky, int kw, int kh, int keyIdx,
                              int idleBg, int pressedBg, int idleText, int pressedText, int idleShadow, int pressedShadow) {
        float t = animFactor[keyIdx];
        int bg = (t == 0.0f) ? idleBg : (t == 1.0f) ? pressedBg : MenuGraphics.mix(idleBg, pressedBg, t);
        int text = (t == 0.0f) ? idleText : (t == 1.0f) ? pressedText : MenuGraphics.mix(idleText, pressedText, t);
        int shadow = (t == 0.0f) ? idleShadow : (t == 1.0f) ? pressedShadow : MenuGraphics.mix(idleShadow, pressedShadow, t);

        MenuGraphics.round(g, kx, ky, kw, kh, bg);

        boolean shadowOn = (t > 0.5f) ? cleanKeyPressedShadow : cleanKeyShadow;
        int tw = g.textWidth(label);
        int tx = kx + (kw - tw) / 2;
        int ty = ky + (kh - g.fontHeight()) / 2 + 1;

        if (shadowOn) {
            if (cleanCustomShadow) {
                g.drawText(label, tx + 1, ty + 1, shadow, false);
                g.drawText(label, tx, ty, text, false);
            } else {
                g.drawText(label, tx, ty, text, true);
            }
        } else {
            g.drawText(label, tx, ty, text, false);
        }
    }

    private void drawCleanSpaceBar(LadsGraphics g, int kx, int ky, int kw, int kh,
                                   int idleBg, int pressedBg, int idleText, int pressedText, int idleShadow, int pressedShadow) {
        float t = animFactor[4];
        int bg = (t == 0.0f) ? idleBg : (t == 1.0f) ? pressedBg : MenuGraphics.mix(idleBg, pressedBg, t);
        int text = (t == 0.0f) ? idleText : (t == 1.0f) ? pressedText : MenuGraphics.mix(idleText, pressedText, t);
        int shadow = (t == 0.0f) ? idleShadow : (t == 1.0f) ? pressedShadow : MenuGraphics.mix(idleShadow, pressedShadow, t);

        MenuGraphics.round(g, kx, ky, kw, kh, bg);

        boolean shadowOn = (t > 0.5f) ? cleanKeyPressedShadow : cleanKeyShadow;
        int bw = 11, bh = 2;
        int bx = kx + (kw - bw) / 2;
        int by = ky + (kh - bh) / 2;

        if (shadowOn) {
            int sCol = cleanCustomShadow ? shadow : (shadow & 0x00FFFFFF) | 0x3F000000;
            g.fill(bx + 1, by + 1, bx + bw + 1, by + bh + 1, sCol);
        }
        g.fill(bx, by, bx + bw, by + bh, text);
    }

    private void drawCleanMousePill(LadsGraphics g, String label, int kx, int ky, int kw, int kh, int keyIdx, boolean leftPill,
                                   int idleBg, int pressedBg, int idleText, int pressedText, int idleShadow, int pressedShadow) {
        float t = animFactor[keyIdx];
        int bg = (t == 0.0f) ? idleBg : (t == 1.0f) ? pressedBg : MenuGraphics.mix(idleBg, pressedBg, t);
        int text = (t == 0.0f) ? idleText : (t == 1.0f) ? pressedText : MenuGraphics.mix(idleText, pressedText, t);
        int shadow = (t == 0.0f) ? idleShadow : (t == 1.0f) ? pressedShadow : MenuGraphics.mix(idleShadow, pressedShadow, t);

        if (leftPill) {
            MenuGraphics.roundLeft(g, kx, ky, kw, kh, bg);
        } else {
            MenuGraphics.roundRight(g, kx, ky, kw, kh, bg);
        }

        boolean shadowOn = (t > 0.5f) ? cleanKeyPressedShadow : cleanKeyShadow;
        int tw = g.textWidth(label);
        int tx = kx + (kw - tw) / 2;
        int ty = ky + (kh - g.fontHeight()) / 2 + 1;

        if (shadowOn) {
            if (cleanCustomShadow) {
                g.drawText(label, tx + 1, ty + 1, shadow, false);
                g.drawText(label, tx, ty, text, false);
            } else {
                g.drawText(label, tx, ty, text, true);
            }
        } else {
            g.drawText(label, tx, ty, text, false);
        }
    }

    private void renderMouseTracker(LadsGraphics g, int bx, int by, int bw, int bh, int bg, int textColor, float delta, long now) {
        if ((bg >>> 24) != 0) {
            g.fill(bx, by, bx + bw, by + bh, bg);
        }

        int cx = bx + bw / 2;
        int cy = by + bh / 2;

        // Static center dot with 50% opacity
        int staticAlpha = (((textColor >>> 24) & 0xFF) / 2);
        if (staticAlpha > 0) {
            int staticCol = (staticAlpha << 24) | (textColor & 0x00FFFFFF);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, staticCol);
        }

        float yaw = g.getGame().getYaw();
        float pitch = g.getGame().getPitch();

        if (yaw < 0 || !g.getGame().isIngame()) {
            mouseSmoothX = 0;
            mouseSmoothY = 0;
            mouseDotX = cx;
            mouseDotY = cy;
            lastYaw = -9999;
            lastPitch = -9999;
        } else {
            if (lastYaw < -9000) {
                lastYaw = yaw;
                lastPitch = pitch;
                mouseDotX = cx;
                mouseDotY = cy;
            }
            float dYaw = yaw - lastYaw;
            float dPitch = pitch - lastPitch;
            lastYaw = yaw;
            lastPitch = pitch;

            while (dYaw > 180) dYaw -= 360;
            while (dYaw < -180) dYaw += 360;

            float decay = (float) Math.pow(0.8, delta);
            mouseSmoothX = mouseSmoothX * decay + dYaw * 0.02f;
            mouseSmoothY = mouseSmoothY * decay + dPitch * 0.02f;
            mouseSmoothX = Math.max(-1.0f, Math.min(1.0f, mouseSmoothX));
            mouseSmoothY = Math.max(-1.0f, Math.min(1.0f, mouseSmoothY));

            float radiusX = (bw - 4) / 2.0f;
            float radiusY = (bh - 4) / 2.0f;
            float targetDotX = cx + mouseSmoothX * radiusX;
            float targetDotY = cy + mouseSmoothY * radiusY;

            mouseDotX += (targetDotX - mouseDotX) * 0.5f;
            mouseDotY += (targetDotY - mouseDotY) * 0.5f;

            if (Math.abs(mouseDotX - lastTrailX) > 0.5f || Math.abs(mouseDotY - lastTrailY) > 0.5f) {
                trailX[trailHead] = mouseDotX;
                trailY[trailHead] = mouseDotY;
                trailTime[trailHead] = now;
                trailHead = (trailHead + 1) % MAX_TRAIL;
                if (trailCount < MAX_TRAIL) trailCount++;
                lastTrailX = mouseDotX;
                lastTrailY = mouseDotY;
            }
        }

        // Draw trail
        for (int i = 0; i < trailCount; i++) {
            int idx = (trailHead - trailCount + i + MAX_TRAIL) % MAX_TRAIL;
            long age = now - trailTime[idx];
            if (age < 300) {
                float fade = 1.0f - (age / 300.0f);
                int trailAlpha = (int) (((textColor >>> 24) & 0xFF) * fade * 0.5f);
                if (trailAlpha > 0) {
                    int trailCol = (trailAlpha << 24) | (textColor & 0x00FFFFFF);
                    int px = Math.round(trailX[idx]);
                    int py = Math.round(trailY[idx]);
                    g.fill(px - 1, py - 1, px + 1, py + 1, trailCol);
                }
            }
        }

        // Draw dynamic moving dot (diameter 3)
        int mx = Math.round(mouseDotX);
        int my = Math.round(mouseDotY);
        g.fill(mx - 1, my - 1, mx + 2, my + 2, textColor);
    }

    private static int rainbow(double phase, double shift, int origAlpha) {
        double p = (phase + shift) % 1.0;
        if (p < 0) p += 1.0;
        int r = (int) ((Math.sin(p * (2 * Math.PI)) * 0.5 + 0.5) * 255);
        int g = (int) ((Math.sin((p + 1.0 / 3.0) * (2 * Math.PI)) * 0.5 + 0.5) * 255);
        int b = (int) ((Math.sin((p + 2.0 / 3.0) * (2 * Math.PI)) * 0.5 + 0.5) * 255);
        return (origAlpha << 24) | (r << 16) | (g << 8) | b;
    }
}
