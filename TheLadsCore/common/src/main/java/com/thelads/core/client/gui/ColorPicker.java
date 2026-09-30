package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import java.awt.Color;
import java.util.function.IntConsumer;

/** Shared modal picker. Changes are previewed here and committed only by Apply. */
public final class ColorPicker {
    private IntConsumer apply;
    private String title, hex;
    private float hue, saturation, value, alpha;
    private int x, y, w, h, planeW, planeH, drag = -1;
    private boolean global, background, hexFocused, replaceHex;
    public boolean isOpen() { return apply != null; }
    public void open(String title, int color, IntConsumer apply) {
        this.title = title; this.apply = apply; global = false; hexFocused = false; drag = -1;
        set(color);
    }
    public void openGlobal() {
        background = false;
        open("Global HUD colors", HudSettings.getInstance().getGlobalColor(), c -> {
            if (background) HudSettings.getInstance().setGlobalBackground(c);
            else HudSettings.getInstance().setGlobalColor(c);
            ConfigManager.save();
        });
        global = true;
    }
    private void set(int c) {
        float[] hsv = Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; alpha = (c >>> 24) / 255f;
        hex = String.format("%08X", c);
    }
    public int color() { return (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF); }
    public void render(LadsGraphics g, int mx, int my) {
        if (!isOpen()) return;
        w = Math.min(430, g.getScaledWidth() - 16); h = Math.min(310, g.getScaledHeight() - 16);
        x = (g.getScaledWidth() - w) / 2; y = (g.getScaledHeight() - h) / 2;
        planeW = w - 70; planeH = Math.max(32, h - 157);
        g.fill(0, 0, g.getScaledWidth(), g.getScaledHeight(), 0xC0000000);
        g.fill(x, y, x + w, y + h, LadsPalette.PANEL);
        g.fill(x, y, x + w, y + 2, LadsPalette.ACCENT);
        g.drawText(title, x + 12, y + 10, LadsPalette.TEXT);
        if (global) {
            int cell=(w-32)/3;
            button(g,x+12,y+28,cell,background?"Background":"Text color");
            button(g,x+16+cell,y+28,cell,"Plates: "+(HudSettings.getInstance().isBackgrounds()?"ON":"OFF"));
            button(g,x+20+cell*2,y+28,cell,"Shadow: "+(HudSettings.getInstance().isTextShadow()?"ON":"OFF"));
        }
        int py = y + 56;
        for (int px = 0; px < planeW; px += 4) for (int yy = 0; yy < planeH; yy += 4)
            g.fill(x + 12 + px, py + yy, x + 12 + Math.min(px + 4, planeW), py + Math.min(yy + 4, planeH),
                Color.HSBtoRGB(hue, px / (float)Math.max(1, planeW - 1), 1 - yy / (float)Math.max(1, planeH - 1)));
        for (int yy = 0; yy < planeH; yy += 2) {
            g.fill(x + w - 48, py + yy, x + w - 34, py + Math.min(yy + 2, planeH), Color.HSBtoRGB(yy / (float)planeH, 1, 1));
            int a = Math.round(255 * (1 - yy / (float)planeH));
            g.fill(x + w - 26, py + yy, x + w - 12, py + Math.min(yy + 2, planeH), 0xFF555555);
            g.fill(x + w - 26, py + yy, x + w - 12, py + Math.min(yy + 2, planeH), (color() & 0xFFFFFF) | a << 24);
        }
        int cx = x + 12 + Math.round(saturation * (planeW - 1)), cy = py + Math.round((1 - value) * (planeH - 1));
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xFFFFFFFF);
        g.fill(x + w - 50, py + Math.round(hue * (planeH - 1)), x + w - 32, py + Math.round(hue * (planeH - 1)) + 2, -1);
        g.fill(x + w - 28, py + Math.round((1-alpha) * (planeH - 1)), x + w - 10, py + Math.round((1-alpha) * (planeH - 1)) + 2, -1);
        int fy = py + planeH + 9;
        button(g, x + 12, fy, 124, "#" + hex + (hexFocused ? "|" : ""));
        g.fill(x + 144, fy, x + 168, fy + 20, 0xFF888888);
        g.fill(x + 144, fy, x + 168, fy + 20, color());
        button(g, x + 178, fy, w - 190, "Favorite color +");
        var favorites = HudSettings.getInstance().getFavoriteColors();
        for (int i = 0; i < Math.min(24, favorites.size()); i++) {
            int sx = x + 12 + i * (w - 24) / 24;
            g.fill(sx, fy + 27, sx + Math.max(5, (w - 24) / 24 - 2), fy + 45, favorites.get(i));
        }
        button(g, x + 12, y + h - 28, (w - 30) / 2, "Cancel");
        button(g, x + 18 + (w - 30) / 2, y + h - 28, (w - 30) / 2, "Apply");
    }
    private static void button(LadsGraphics g, int x, int y, int w, String text) {
        g.fill(x, y, x + w, y + 20, LadsPalette.CARD);
        g.drawCenteredText(text, x + w / 2, y + 6, LadsPalette.TEXT);
    }
    public boolean click(double mx, double my, int button) {
        if (!isOpen()) return false;
        if (button != 0) return true;
        hexFocused = false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return true;
        if (my >= y + h - 28) {
            if (mx >= x + w / 2 && validHex()) apply.accept(color());
            else if (mx >= x + w / 2) return true;
            apply = null; return true;
        }
        if (global && my >= y + 28 && my < y + 48) {
            if (mx < x + 14 + (w-32)/3) { background = !background; set(background ? HudSettings.getInstance().getGlobalBackground() : HudSettings.getInstance().getGlobalColor()); }
            else if (mx < x + 18 + 2*((w-32)/3)) HudSettings.getInstance().setBackgrounds(!HudSettings.getInstance().isBackgrounds());
            else HudSettings.getInstance().setTextShadow(!HudSettings.getInstance().isTextShadow());
            ConfigManager.save(); return true;
        }
        int fy = y + 56 + planeH + 9;
        if (my >= fy && my < fy + 20) {
            if (mx < x + 136) { hexFocused = true; replaceHex = true; }
            else if (mx >= x + 178 && validHex()) {
                var favorites = HudSettings.getInstance().getFavoriteColors();
                if (!favorites.contains(color()) && favorites.size() < 24) { favorites.add(color()); ConfigManager.save(); }
            }
            return true;
        }
        if (my >= fy + 27 && my < fy + 45) {
            int i = (int)((mx - x - 12) * 24 / (w - 24));
            var favorites = HudSettings.getInstance().getFavoriteColors();
            if (i >= 0 && i < favorites.size()) set(favorites.get(i));
            return true;
        }
        if (my >= y + 56 && my < y + 56 + planeH) {
            drag = mx >= x + w - 28 ? 2 : mx >= x + w - 50 ? 1 : 0;
            move(mx, my);
        }
        return true;
    }
    public boolean move(double mx, double my) {
        if (!isOpen()) return false;
        float t = (float)Math.max(0, Math.min(1, (my - y - 56) / Math.max(1, planeH - 1)));
        if (drag == 0) { saturation = (float)Math.max(0, Math.min(1, (mx - x - 12) / Math.max(1, planeW - 1))); value = 1 - t; }
        if (drag == 1) hue = t;
        if (drag == 2) alpha = 1 - t;
        if (drag >= 0) hex = String.format("%08X", color());
        return true;
    }
    public boolean release() { drag = -1; return isOpen(); }
    public boolean key(int key) {
        if (!isOpen()) return false;
        if (key == 256) apply = null;
        if (hexFocused && key == 259 && !hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
        if (key == 257 && validHex()) { apply.accept(color()); apply = null; }
        return true;
    }
    public boolean type(int codePoint) {
        if (!isOpen()) return false;
        if (hexFocused && Character.digit(codePoint, 16) >= 0) {
            if (replaceHex) { hex = ""; replaceHex = false; }
            if (hex.length() < 8) hex += Character.toUpperCase((char)codePoint);
            validHex();
        }
        return true;
    }
    private boolean validHex() {
        if (hex.length() != 8) return false;
        try { set(Integer.parseUnsignedInt(hex, 16)); return true; } catch (NumberFormatException e) { return false; }
    }
}
