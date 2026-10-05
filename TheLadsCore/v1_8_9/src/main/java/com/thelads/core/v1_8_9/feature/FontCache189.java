package com.thelads.core.v1_8_9.feature;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * String width cache of one FontRenderer (FontRendererMixin; 1.7.3). 1.8.9 measures text a character at a time, and the
 * scoreboard, tab list, nametags, chat and HUD measure the same strings every frame. A font keeps one cache for its normal and one
 * for its Unicode glyphs (screens switch Unicode on while they draw). Everything is dropped on a resource reload (new font
 * textures) and when the bidi setting changes. Client thread only (Forge's loading screen measures on another).
 * -Dthelads.fontCache=false turns it off; it also stays off next to Patcher, which has its own.
 * A display-list cache of drawn strings was tried too: pixel-identical, but no measurable gain over the whole HUD (one run was
 * slower), so 1.7.3 keeps widths only (artifacts/1.7.3/hud).
 */
public final class FontCache189 {
    /** What the mixin adds to FontRenderer: the cache for its current Unicode setting. */
    public interface Holder {
        FontCache189 ladsCache();
    }

    private static final int MAX_WIDTHS = 5000;
    private static Boolean enabled;
    /** QA only (Probe172HudFlicker): measured without the cache, for comparison. */
    static boolean qaOff;

    private final FontRenderer font;
    private final Map<String, Integer> widths = new HashMap<String, Integer>();
    private boolean bidi;
    private Object epoch = new Object();

    public FontCache189(FontRenderer font) {
        this.font = font;
        bidi = font.getBidiFlag();
    }

    /** The key the HUD measures text against: it changes whenever this font's metrics may have changed. */
    public static Object metricsKey(FontRenderer font) {
        if (!(font instanceof Holder)) return null;
        FontCache189 cache = ((Holder) font).ladsCache();
        cache.checkFlags();
        return cache.epoch;
    }

    private static boolean enabled() {
        if (enabled == null) enabled = !"false".equals(System.getProperty("thelads.fontCache"))
            && !net.minecraftforge.fml.common.Loader.isModLoaded("patcher");
        return enabled;
    }

    /** Whether the cache works here: on, and on the client thread. */
    public boolean on() {
        return !qaOff && enabled() && Minecraft.getMinecraft() != null && Minecraft.getMinecraft().isCallingFromMinecraftThread();
    }

    /** A cached width, or -1 (measure, then {@link #putWidth}). */
    public int width(String text) {
        checkFlags();
        Integer width = widths.get(text);
        return width != null ? width : -1;
    }

    public void putWidth(String text, int width) {
        if (widths.size() >= MAX_WIDTHS) widths.clear();
        widths.put(text, width);
    }

    private void checkFlags() {
        if (font.getBidiFlag() == bidi) return;
        bidi = font.getBidiFlag();
        clear();
    }

    /** New font textures and widths (resource reload). */
    public void clear() {
        widths.clear();
        epoch = new Object();
    }
}
