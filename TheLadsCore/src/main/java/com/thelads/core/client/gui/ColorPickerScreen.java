package com.thelads.core.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Advanced colour picker: a saturation/value box, a hue slider, preset swatches
 * and direct hex input. Returns the chosen colour (and optionally a "use global"
 * flag) to a callback. Pure render/input code — no mixins.
 */
public class ColorPickerScreen extends Screen {

    public interface ColorCallback {
        void apply(boolean useGlobal, int color);
    }

    private static final int BG          = 0xEE101020;
    private static final int CARD        = 0xCC1A1A2E;
    private static final int ACCENT      = 0xFF6C63FF;
    private static final int TEXT        = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFFAAAAAA;

    private static final int[] PRESETS = {
        0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
        0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFAA00FF, 0xFFFF55FF
    };

    // layout
    private static final int SV_X = 40, SV_Y = 72, SV_SIZE = 168, CELLS = 24;
    private static final int HUE_X = 222, HUE_Y = 72, HUE_W = 18, HUE_H = 168;
    private static final int PV_X = 252, PV_Y = 72, PV = 56;

    private final Screen parent;
    private final boolean allowGlobal;
    private final ColorCallback callback;

    private float hue, sat, val;          // HSV source of truth
    private int alpha = 255;              // 0-255 transparency
    private boolean useGlobal;
    private boolean draggingSV = false, draggingHue = false, draggingAlpha = false;
    private boolean hexFocused = false;
    private String hexBuffer = "";

    public ColorPickerScreen(Screen parent, int initialColor, boolean allowGlobal, boolean initialUseGlobal, ColorCallback callback) {
        super(Component.literal("Color Picker"));
        this.parent = parent;
        this.allowGlobal = allowGlobal;
        this.useGlobal = initialUseGlobal;
        this.callback = callback;
        setFromRgb(initialColor);
    }

    private int currentColor() {
        return (alpha << 24) | (Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    private void setFromRgb(int rgb) {
        int a = (rgb >>> 24) & 0xFF;
        this.alpha = (a == 0) ? 255 : a;
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        val = max;
        sat = (max == 0f) ? 0f : d / max;
        float h;
        if (d == 0f) {
            h = 0f;
        } else if (max == r) {
            h = (((g - b) / d) % 6f);
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        h /= 6f;
        if (h < 0f) h += 1f;
        hue = h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BG);
        g.text(this.font, "§l§5Color Picker", SV_X, 40, TEXT, true);

        // --- Saturation / Value box for the current hue ---
        int cell = SV_SIZE / CELLS;
        for (int i = 0; i < CELLS; i++) {
            for (int j = 0; j < CELLS; j++) {
                float s = i / (float) (CELLS - 1);
                float v = 1f - j / (float) (CELLS - 1);
                int col = Mth.hsvToRgb(hue, s, v) | 0xFF000000;
                g.fill(SV_X + i * cell, SV_Y + j * cell, SV_X + i * cell + cell, SV_Y + j * cell + cell, col);
            }
        }
        // SV cursor
        int cx = SV_X + (int) (sat * SV_SIZE);
        int cy = SV_Y + (int) ((1f - val) * SV_SIZE);
        drawRing(g, cx, cy, 0xFFFFFFFF);

        // --- Hue slider ---
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            int col = Mth.hsvToRgb(i / (float) (segs - 1), 1f, 1f) | 0xFF000000;
            int y0 = HUE_Y + i * HUE_H / segs;
            int y1 = HUE_Y + (i + 1) * HUE_H / segs;
            g.fill(HUE_X, y0, HUE_X + HUE_W, y1, col);
        }
        int hcy = HUE_Y + (int) (hue * HUE_H);
        g.fill(HUE_X - 2, hcy - 1, HUE_X + HUE_W + 2, hcy + 1, 0xFFFFFFFF);

        // --- Preview + hex ---
        g.fill(PV_X - 1, PV_Y - 1, PV_X + PV + 1, PV_Y + PV + 1, 0xFF000000);
        g.fill(PV_X, PV_Y, PV_X + PV, PV_Y + PV, currentColor());

        String hex = (alpha < 255) ? String.format("#%08X", currentColor()) : String.format("#%06X", currentColor() & 0xFFFFFF);
        String hexShown = hexFocused ? ("#" + hexBuffer + "_") : hex;
        g.fill(PV_X, PV_Y + PV + 8, PV_X + PV + 24, PV_Y + PV + 24, hexFocused ? 0xFF2A2A44 : CARD);
        g.text(this.font, hexShown, PV_X + 4, PV_Y + PV + 12, hexFocused ? ACCENT : TEXT, true);
        g.text(this.font, "§7click hex to type", PV_X, PV_Y + PV + 28, TEXT_DIM, false);

        // --- Use Global toggle ---
        int rowY = PV_Y + PV + 46;
        if (allowGlobal) {
            g.fill(PV_X, rowY, PV_X + 16, rowY + 16, useGlobal ? ACCENT : 0xFF333350);
            if (useGlobal) {
                g.text(this.font, "§f✔", PV_X + 4, rowY + 4, TEXT, false);
            }
            g.text(this.font, "Use Global Color", PV_X + 22, rowY + 4, TEXT, false);
            rowY += 24;
        }

        // --- Alpha (transparency) slider ---
        int baseRgb = Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
        int ay = SV_Y + SV_SIZE + 6;
        g.fill(SV_X - 1, ay - 1, SV_X + SV_SIZE + 1, ay + 11, 0xFF000000);
        int aSegs = 32;
        for (int i = 0; i < aSegs; i++) {
            int a = (int) (255f * i / (aSegs - 1));
            int x0 = SV_X + i * SV_SIZE / aSegs;
            int x1 = SV_X + (i + 1) * SV_SIZE / aSegs;
            g.fill(x0, ay, x1, ay + 10, 0xFFFFFFFF);
            g.fill(x0, ay, x1, ay + 10, (a << 24) | baseRgb);
        }
        int acx = SV_X + (int) (alpha / 255f * SV_SIZE);
        g.fill(acx - 1, ay - 2, acx + 2, ay + 12, 0xFFFFFFFF);
        g.fill(acx, ay - 1, acx + 1, ay + 11, 0xFF000000);
        g.text(this.font, "§7Alpha " + alpha, SV_X, ay + 13, TEXT_DIM, false);

        // --- Presets ---
        for (int i = 0; i < PRESETS.length; i++) {
            int x = SV_X + i * 20;
            int y = SV_Y + SV_SIZE + 36;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
            g.fill(x, y, x + 16, y + 16, PRESETS[i] | 0xFF000000);
        }

        // --- Apply / Cancel ---
        drawButton(g, PV_X, rowY, 100, 22, "§fApply", mouseX, mouseY, ACCENT);
        drawButton(g, PV_X, rowY + 28, 100, 22, "§fCancel", mouseX, mouseY, 0xFF444466);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawRing(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx - 3, cy - 1, cx + 3, cy, color);
        g.fill(cx - 3, cy + 1, cx + 3, cy + 2, color);
        g.fill(cx - 1, cy - 3, cx, cy + 3, color);
        g.fill(cx + 1, cy - 3, cx + 2, cy + 3, color);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY, int color) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hov ? (color | 0x22FFFFFF) : color);
        g.text(this.font, label, x + w / 2 - this.font.width(label) / 2, y + (h - 8) / 2, TEXT, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            // SV box
            if (mx >= SV_X && mx < SV_X + SV_SIZE && my >= SV_Y && my < SV_Y + SV_SIZE) {
                draggingSV = true;
                hexFocused = false;
                updateSV(mx, my);
                return true;
            }
            // Hue slider
            if (mx >= HUE_X && mx < HUE_X + HUE_W && my >= HUE_Y && my < HUE_Y + HUE_H) {
                draggingHue = true;
                hexFocused = false;
                updateHue(my);
                return true;
            }
            // Alpha slider
            int ay = SV_Y + SV_SIZE + 6;
            if (mx >= SV_X && mx < SV_X + SV_SIZE && my >= ay - 2 && my < ay + 12) {
                draggingAlpha = true;
                hexFocused = false;
                updateAlpha(mx);
                return true;
            }
            // Presets
            for (int i = 0; i < PRESETS.length; i++) {
                int x = SV_X + i * 20;
                int y = SV_Y + SV_SIZE + 36;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    int keepAlpha = alpha;
                    setFromRgb(PRESETS[i]);
                    alpha = keepAlpha; // presets only set hue/sat/val, keep current alpha
                    hexFocused = false;
                    return true;
                }
            }
            // Hex field
            if (mx >= PV_X && mx < PV_X + PV + 24 && my >= PV_Y + PV + 8 && my < PV_Y + PV + 24) {
                hexFocused = true;
                hexBuffer = String.format("%06X", currentColor() & 0xFFFFFF);
                return true;
            }
            int rowY = PV_Y + PV + 46;
            if (allowGlobal) {
                if (mx >= PV_X && mx < PV_X + 16 && my >= rowY && my < rowY + 16) {
                    useGlobal = !useGlobal;
                    return true;
                }
                rowY += 24;
            }
            // Apply
            if (mx >= PV_X && mx < PV_X + 100 && my >= rowY && my < rowY + 22) {
                if (callback != null) callback.apply(useGlobal, currentColor());
                this.minecraft.setScreenAndShow(parent);
                return true;
            }
            // Cancel
            if (mx >= PV_X && mx < PV_X + 100 && my >= rowY + 28 && my < rowY + 50) {
                this.minecraft.setScreenAndShow(parent);
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSV) { updateSV(event.x(), event.y()); return true; }
        if (draggingHue) { updateHue(event.y()); return true; }
        if (draggingAlpha) { updateAlpha(event.x()); return true; }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSV = false;
        draggingHue = false;
        draggingAlpha = false;
        return super.mouseReleased(event);
    }

    private void updateAlpha(double mx) {
        alpha = (int) (Mth.clamp((float) (mx - SV_X) / SV_SIZE, 0f, 1f) * 255f);
    }

    private void updateSV(double mx, double my) {
        sat = Mth.clamp((float) (mx - SV_X) / SV_SIZE, 0f, 1f);
        val = Mth.clamp(1f - (float) (my - SV_Y) / SV_SIZE, 0f, 1f);
    }

    private void updateHue(double my) {
        hue = Mth.clamp((float) (my - HUE_Y) / HUE_H, 0f, 1f);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (hexFocused) {
            if (k == 256) { hexFocused = false; return true; }            // Esc
            if (k == 259) {                                               // Backspace
                if (hexBuffer.length() > 0) hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                return true;
            }
            if (k == 257 || k == 335) {                                  // Enter
                applyHex();
                return true;
            }
            char c = 0;
            if (k >= 48 && k <= 57) c = (char) ('0' + (k - 48));
            else if (k >= 65 && k <= 70) c = (char) ('A' + (k - 65));
            if (c != 0 && hexBuffer.length() < 8) {
                hexBuffer += c;
                return true;
            }
            return true; // swallow other keys while editing
        }
        if (k == 256) { this.minecraft.setScreenAndShow(parent); return true; }
        return super.keyPressed(event);
    }

    private void applyHex() {
        try {
            String s = hexBuffer;
            if (s.length() == 3) {
                // expand shorthand like F0A -> FF00AA
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) { sb.append(s.charAt(i)).append(s.charAt(i)); }
                s = sb.toString();
            }
            if (s.length() == 6) {
                int rgb = Integer.parseInt(s, 16) & 0xFFFFFF;
                setFromRgb(0xFF000000 | rgb);
            } else if (s.length() == 8) {
                int argb = (int) Long.parseLong(s, 16);
                setFromRgb(argb); // reads alpha + rgb
            }
        } catch (NumberFormatException ignored) {
        }
        hexFocused = false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
