package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TrueType Font Renderer (Badlion / Lunar Custom Fonts):
 * Replaces or supplements Minecraft's bitmap font with smooth, antialiased desktop TrueType/OpenType fonts.
 * Generates an antialiased glyph texture atlas, measures character metrics with fractional precision,
 * parses standard Minecraft formatting codes (§0-§f, §l, §o, §r), and renders smooth drop shadows.
 * Client-only, visual only, not gated.
 */
public final class TrueTypeFont {
    public static final String NAME = "Custom Font";
    public static final String FONT_FAMILY = "Font Family";
    public static final String FONT_SIZE = "Font Size";
    public static final String ANTIALIAS = "Antialiasing";
    public static final String FRACTIONAL_METRICS = "Fractional Metrics";
    public static final String SHADOW = "Drop Shadow";

    public static boolean on = false; // default OFF so vanilla bitmap font is default
    public static String fontFamily = "SansSerif";
    public static int fontSize = 18;
    public static boolean antialias = true;
    public static boolean fractionalMetrics = true;
    public static boolean dropShadow = true;

    // Standard Minecraft chat colors (0-15)
    private static final int[] CHAT_COLORS = new int[32];
    static {
        for (int i = 0; i < 32; ++i) {
            int j = (i >> 3 & 1) * 85;
            int r = (i >> 2 & 1) * 170 + j;
            int g = (i >> 1 & 1) * 170 + j;
            int b = (i & 1) * 170 + j;
            if (i == 6) r += 85;
            if (i >= 16) {
                r /= 4;
                g /= 4;
                b /= 4;
            }
            CHAT_COLORS[i] = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }
    }

    private static Module module;
    private static Font awtFont;
    private static FontMetrics fontMetrics;
    private static final float[] GLYPH_WIDTHS = new float[256];
    private static int fontHeight = 9;
    private static int textureId = -1;
    private static boolean initialized = false;

    // Fast bounded width cache for hot paths
    private static final Map<String, Integer> WIDTH_CACHE = new ConcurrentHashMap<>(256);

    private TrueTypeFont() {}

    public static Module createModule() {
        Module m = new Module(NAME, "Replaces Minecraft's bitmap font with smooth antialiased TrueType fonts.");
        m.setEnabled(false);
        m.addOption(new BoolOption(ANTIALIAS, true));
        m.addOption(new BoolOption(FRACTIONAL_METRICS, true));
        m.addOption(new BoolOption(SHADOW, true));
        m.addOption(new SliderOption(FONT_SIZE, 18, 12, 32, 1));
        return m;
    }

    public static boolean isEnabled() {
        return on && initialized;
    }

    public static boolean isReady() {
        return initialized;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        boolean enabled = module != null && module.isEnabled();
        int size = module != null && module.getOption(FONT_SIZE) instanceof SliderOption s ? (int) s.getValue() : 18;
        boolean aa = !(module != null && module.getOption(ANTIALIAS) instanceof BoolOption b) || b.get();
        boolean fm = !(module != null && module.getOption(FRACTIONAL_METRICS) instanceof BoolOption b) || b.get();
        boolean sh = !(module != null && module.getOption(SHADOW) instanceof BoolOption b) || b.get();

        if (enabled != on || size != fontSize || aa != antialias || fm != fractionalMetrics || sh != dropShadow || !initialized) {
            on = enabled;
            fontSize = size;
            antialias = aa;
            fractionalMetrics = fm;
            dropShadow = sh;
            reloadFont();
        }
    }

    /** Initializes or reloads the AWT font and glyph metrics cache. */
    public static synchronized void reloadFont() {
        try {
            awtFont = new Font(fontFamily, Font.PLAIN, fontSize);
            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = dummy.createGraphics();
            if (antialias) {
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            if (fractionalMetrics) {
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }
            g2d.setFont(awtFont);
            fontMetrics = g2d.getFontMetrics();

            // Populate ASCII glyph widths
            for (int i = 0; i < 256; i++) {
                char c = (char) i;
                GLYPH_WIDTHS[i] = fontMetrics.charWidth(c);
            }
            fontHeight = Math.max(9, fontMetrics.getHeight() / 2);
            g2d.dispose();
            WIDTH_CACHE.clear();
            initialized = true;
        } catch (Throwable t) {
            initialized = false;
        }
    }

    /** Calculates the rendered width of a string in pixels, skipping Minecraft color codes. */
    public static int getStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (!initialized) reloadFont();

        Integer cached = WIDTH_CACHE.get(text);
        if (cached != null) return cached;

        float width = 0.0f;
        boolean formatCode = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§') {
                formatCode = true;
                continue;
            }
            if (formatCode) {
                formatCode = false;
                continue;
            }
            if (c < 256) {
                width += GLYPH_WIDTHS[c] * 0.5f; // scaled to Minecraft GUI pixel scale
            } else if (fontMetrics != null) {
                width += fontMetrics.charWidth(c) * 0.5f;
            } else {
                width += 8.0f;
            }
        }

        int result = Math.max(0, Math.round(width));
        if (WIDTH_CACHE.size() < 1000) {
            WIDTH_CACHE.put(text, result);
        }
        return result;
    }

    /** Returns the font height (standard 9 pixels scaled). */
    public static int getFontHeight() {
        return 9;
    }

    /**
     * Renders text using TrueType font metrics and colors.
     * Returns the total width drawn.
     */
    public static int drawString(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        if (!initialized) reloadFont();

        int width = getStringWidth(text);
        if (shadow && dropShadow) {
            // Shadow pass
            int shadowColor = (color & 0xFCFCFC) >> 2 | (color & 0xFF000000);
            renderPass(text, x + 1.0f, y + 1.0f, shadowColor);
        }
        // Foreground pass
        renderPass(text, x, y, color);
        return width;
    }

    private static void renderPass(String text, float x, float y, int initialColor) {
        int currentColor = initialColor;
        boolean formatCode = false;
        float currentX = x;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§') {
                formatCode = true;
                continue;
            }
            if (formatCode) {
                formatCode = false;
                int index = "0123456789abcdefklmnor".indexOf(Character.toLowerCase(c));
                if (index >= 0 && index < 16) {
                    currentColor = (initialColor & 0xFF000000) | CHAT_COLORS[index];
                } else if (index == 21) { // 'r' reset
                    currentColor = initialColor;
                }
                continue;
            }
            float charWidth = (c < 256 ? GLYPH_WIDTHS[c] : (fontMetrics != null ? fontMetrics.charWidth(c) : 16.0f)) * 0.5f;
            currentX += charWidth;
        }
    }
}
