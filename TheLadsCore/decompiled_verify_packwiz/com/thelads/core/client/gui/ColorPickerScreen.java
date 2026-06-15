/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.Mth
 */
package com.thelads.core.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ColorPickerScreen
extends Screen {
    private static final int BG = -300937184;
    private static final int CARD = -870704594;
    private static final int ACCENT = -9673729;
    private static final int TEXT = -1;
    private static final int TEXT_DIM = -5592406;
    private static final int[] PRESETS = new int[]{-1, -16777216, -43691, -22016, -171, -11141291, -11141121, -11184641, -5635841, -43521};
    private static final int SV_X = 40;
    private static final int SV_Y = 72;
    private static final int SV_SIZE = 168;
    private static final int CELLS = 24;
    private static final int HUE_X = 222;
    private static final int HUE_Y = 72;
    private static final int HUE_W = 18;
    private static final int HUE_H = 168;
    private static final int PV_X = 252;
    private static final int PV_Y = 72;
    private static final int PV = 56;
    private final Screen parent;
    private final boolean allowGlobal;
    private final ColorCallback callback;
    private float hue;
    private float sat;
    private float val;
    private int alpha = 255;
    private boolean useGlobal;
    private boolean draggingSV = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;
    private boolean hexFocused = false;
    private String hexBuffer = "";

    public ColorPickerScreen(Screen parent, int initialColor, boolean allowGlobal, boolean initialUseGlobal, ColorCallback callback) {
        super((Component)Component.literal((String)"Color Picker"));
        this.parent = parent;
        this.allowGlobal = allowGlobal;
        this.useGlobal = initialUseGlobal;
        this.callback = callback;
        this.setFromRgb(initialColor);
    }

    private int currentColor() {
        return this.alpha << 24 | Mth.hsvToRgb((float)this.hue, (float)this.sat, (float)this.val) & 0xFFFFFF;
    }

    private void setFromRgb(int rgb) {
        int a = rgb >>> 24 & 0xFF;
        this.alpha = a == 0 ? 255 : a;
        float r = (float)(rgb >> 16 & 0xFF) / 255.0f;
        float g = (float)(rgb >> 8 & 0xFF) / 255.0f;
        float b = (float)(rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        this.val = max;
        float f = this.sat = max == 0.0f ? 0.0f : d / max;
        float h = d == 0.0f ? 0.0f : (max == r ? (g - b) / d % 6.0f : (max == g ? (b - r) / d + 2.0f : (r - g) / d + 4.0f));
        if ((h /= 6.0f) < 0.0f) {
            h += 1.0f;
        }
        this.hue = h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int col;
        g.fill(0, 0, this.width, this.height, -300937184);
        g.text(this.font, "\u00a7l\u00a75Color Picker", 40, 40, -1, true);
        int cell = 7;
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                float s = (float)i / 23.0f;
                float v = 1.0f - (float)j / 23.0f;
                col = Mth.hsvToRgb((float)this.hue, (float)s, (float)v) | 0xFF000000;
                g.fill(40 + i * cell, 72 + j * cell, 40 + i * cell + cell, 72 + j * cell + cell, col);
            }
        }
        int cx = 40 + (int)(this.sat * 168.0f);
        int cy = 72 + (int)((1.0f - this.val) * 168.0f);
        this.drawRing(g, cx, cy, -1);
        int segs = 32;
        for (int i = 0; i < segs; ++i) {
            col = Mth.hsvToRgb((float)((float)i / (float)(segs - 1)), (float)1.0f, (float)1.0f) | 0xFF000000;
            int y0 = 72 + i * 168 / segs;
            int y1 = 72 + (i + 1) * 168 / segs;
            g.fill(222, y0, 240, y1, col);
        }
        int hcy = 72 + (int)(this.hue * 168.0f);
        g.fill(220, hcy - 1, 242, hcy + 1, -1);
        g.fill(251, 71, 309, 129, -16777216);
        g.fill(252, 72, 308, 128, this.currentColor());
        String hex = this.alpha < 255 ? String.format("#%08X", this.currentColor()) : String.format("#%06X", this.currentColor() & 0xFFFFFF);
        String hexShown = this.hexFocused ? "#" + this.hexBuffer + "_" : hex;
        g.fill(252, 136, 332, 152, this.hexFocused ? -14013884 : -870704594);
        g.text(this.font, hexShown, 256, 140, this.hexFocused ? -9673729 : -1, true);
        g.text(this.font, "\u00a77click hex to type", 252, 156, -5592406, false);
        int rowY = 174;
        if (this.allowGlobal) {
            g.fill(252, rowY, 268, rowY + 16, this.useGlobal ? -9673729 : -13421744);
            if (this.useGlobal) {
                g.text(this.font, "\u00a7f\u2714", 256, rowY + 4, -1, false);
            }
            g.text(this.font, "Use Global Color", 274, rowY + 4, -1, false);
            rowY += 24;
        }
        int baseRgb = Mth.hsvToRgb((float)this.hue, (float)this.sat, (float)this.val) & 0xFFFFFF;
        int ay = 246;
        g.fill(39, ay - 1, 209, ay + 11, -16777216);
        int aSegs = 32;
        for (int i = 0; i < aSegs; ++i) {
            int a = (int)(255.0f * (float)i / (float)(aSegs - 1));
            int x0 = 40 + i * 168 / aSegs;
            int x1 = 40 + (i + 1) * 168 / aSegs;
            g.fill(x0, ay, x1, ay + 10, -1);
            g.fill(x0, ay, x1, ay + 10, a << 24 | baseRgb);
        }
        int acx = 40 + (int)((float)this.alpha / 255.0f * 168.0f);
        g.fill(acx - 1, ay - 2, acx + 2, ay + 12, -1);
        g.fill(acx, ay - 1, acx + 1, ay + 11, -16777216);
        g.text(this.font, "\u00a77Alpha " + this.alpha, 40, ay + 13, -5592406, false);
        for (int i = 0; i < PRESETS.length; ++i) {
            int x = 40 + i * 20;
            int y = 276;
            g.fill(x - 1, y - 1, x + 17, y + 17, -16777216);
            g.fill(x, y, x + 16, y + 16, PRESETS[i] | 0xFF000000);
        }
        this.drawButton(g, 252, rowY, 100, 22, "\u00a7fApply", mouseX, mouseY, -9673729);
        this.drawButton(g, 252, rowY + 28, 100, 22, "\u00a7fCancel", mouseX, mouseY, -12303258);
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
        g.fill(x, y, x + w, y + h, hov ? color | 0x22FFFFFF : color);
        g.text(this.font, label, x + w / 2 - this.font.width(label) / 2, y + (h - 8) / 2, -1, true);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDouble) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            if (mx >= 40.0 && mx < 208.0 && my >= 72.0 && my < 240.0) {
                this.draggingSV = true;
                this.hexFocused = false;
                this.updateSV(mx, my);
                return true;
            }
            if (mx >= 222.0 && mx < 240.0 && my >= 72.0 && my < 240.0) {
                this.draggingHue = true;
                this.hexFocused = false;
                this.updateHue(my);
                return true;
            }
            int ay = 246;
            if (mx >= 40.0 && mx < 208.0 && my >= (double)(ay - 2) && my < (double)(ay + 12)) {
                this.draggingAlpha = true;
                this.hexFocused = false;
                this.updateAlpha(mx);
                return true;
            }
            for (int i = 0; i < PRESETS.length; ++i) {
                int x = 40 + i * 20;
                int y = 276;
                if (!(mx >= (double)x) || !(mx < (double)(x + 16)) || !(my >= (double)y) || !(my < (double)(y + 16))) continue;
                int keepAlpha = this.alpha;
                this.setFromRgb(PRESETS[i]);
                this.alpha = keepAlpha;
                this.hexFocused = false;
                return true;
            }
            if (mx >= 252.0 && mx < 332.0 && my >= 136.0 && my < 152.0) {
                this.hexFocused = true;
                this.hexBuffer = String.format("%06X", this.currentColor() & 0xFFFFFF);
                return true;
            }
            int rowY = 174;
            if (this.allowGlobal) {
                if (mx >= 252.0 && mx < 268.0 && my >= (double)rowY && my < (double)(rowY + 16)) {
                    this.useGlobal = !this.useGlobal;
                    return true;
                }
                rowY += 24;
            }
            if (mx >= 252.0 && mx < 352.0 && my >= (double)rowY && my < (double)(rowY + 22)) {
                if (this.callback != null) {
                    this.callback.apply(this.useGlobal, this.currentColor());
                }
                this.minecraft.setScreen(this.parent);
                return true;
            }
            if (mx >= 252.0 && mx < 352.0 && my >= (double)(rowY + 28) && my < (double)(rowY + 50)) {
                this.minecraft.setScreen(this.parent);
                return true;
            }
        }
        return super.mouseClicked(event, isDouble);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingSV) {
            this.updateSV(event.x(), event.y());
            return true;
        }
        if (this.draggingHue) {
            this.updateHue(event.y());
            return true;
        }
        if (this.draggingAlpha) {
            this.updateAlpha(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingSV = false;
        this.draggingHue = false;
        this.draggingAlpha = false;
        return super.mouseReleased(event);
    }

    private void updateAlpha(double mx) {
        this.alpha = (int)(Mth.clamp((float)((float)(mx - 40.0) / 168.0f), (float)0.0f, (float)1.0f) * 255.0f);
    }

    private void updateSV(double mx, double my) {
        this.sat = Mth.clamp((float)((float)(mx - 40.0) / 168.0f), (float)0.0f, (float)1.0f);
        this.val = Mth.clamp((float)(1.0f - (float)(my - 72.0) / 168.0f), (float)0.0f, (float)1.0f);
    }

    private void updateHue(double my) {
        this.hue = Mth.clamp((float)((float)(my - 72.0) / 168.0f), (float)0.0f, (float)1.0f);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (this.hexFocused) {
            if (k == 256) {
                this.hexFocused = false;
                return true;
            }
            if (k == 259) {
                if (this.hexBuffer.length() > 0) {
                    this.hexBuffer = this.hexBuffer.substring(0, this.hexBuffer.length() - 1);
                }
                return true;
            }
            if (k == 257 || k == 335) {
                this.applyHex();
                return true;
            }
            char c = '\u0000';
            if (k >= 48 && k <= 57) {
                c = (char)(48 + (k - 48));
            } else if (k >= 65 && k <= 70) {
                c = (char)(65 + (k - 65));
            }
            if (c != '\u0000' && this.hexBuffer.length() < 8) {
                this.hexBuffer = this.hexBuffer + c;
                return true;
            }
            return true;
        }
        if (k == 256) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    private void applyHex() {
        try {
            String s = this.hexBuffer;
            if (s.length() == 3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; ++i) {
                    sb.append(s.charAt(i)).append(s.charAt(i));
                }
                s = sb.toString();
            }
            if (s.length() == 6) {
                int rgb = Integer.parseInt(s, 16) & 0xFFFFFF;
                this.setFromRgb(0xFF000000 | rgb);
            } else if (s.length() == 8) {
                int argb = (int)Long.parseLong(s, 16);
                this.setFromRgb(argb);
            }
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
        this.hexFocused = false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static interface ColorCallback {
        public void apply(boolean var1, int var2);
    }
}

