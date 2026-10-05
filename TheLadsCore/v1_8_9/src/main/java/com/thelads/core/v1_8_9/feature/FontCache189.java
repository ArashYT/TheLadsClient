package com.thelads.core.v1_8_9.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Text cache of one FontRenderer (FontRendererMixin; 1.7.3). 1.8.9 measures and draws text a character at a time in immediate
 * mode, and the scoreboard, tab list, nametags, chat and HUD draw the same strings every frame. Widths are kept per string, and a
 * string drawn on two frames is compiled into a display list, keyed by text, colour and shadow pass, drawn at the origin and moved
 * into place. What is drawn is exactly what FontRenderer drew (vanilla, Unicode, OptiFine HD and custom fonts and colours alike):
 * the list records its calls. Not cached: obfuscated text (§k, it changes every frame), underline and strikethrough (§n, §m,
 * drawn with other GL state), other threads (Forge's loading screen). A font keeps one cache for its normal and one for its Unicode
 * glyphs (screens switch Unicode on while they draw). Everything is dropped on a resource reload (new font textures) and when the
 * bidi setting changes; lists unused for 5 seconds are freed. -Dthelads.fontCache=false turns it
 * off; it also stays off next to Patcher, which has its own.
 */
public final class FontCache189 {
    /** What the mixin adds to FontRenderer: the cache for its current Unicode setting. */
    public interface Holder {
        FontCache189 ladsCache();
    }

    private static final int MAX_WIDTHS = 5000, MAX_LISTS = 4000, UNUSED_TICKS = 100;
    private static final List<FontCache189> ALL = new ArrayList<FontCache189>();
    private static Boolean enabled;
    /** QA only (Probe172HudFlicker): drawn and measured without the cache, for comparison. */
    static boolean qaOff;
    private static int tick;

    private final FontRenderer font;
    private final Map<String, Integer> widths = new HashMap<String, Integer>();
    private final Map<Key, Entry> lists = new HashMap<Key, Entry>();
    private final Key probe = new Key();
    private boolean bidi;
    private Object epoch = new Object();

    public FontCache189(FontRenderer font) {
        this.font = font;
        bidi = font.getBidiFlag();
        synchronized (ALL) { ALL.add(this); }
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

    private static boolean usable() {
        return !qaOff && enabled() && Minecraft.getMinecraft() != null && Minecraft.getMinecraft().isCallingFromMinecraftThread();
    }

    /** Whether the cache works here: on, and on the client thread. */
    public boolean on() {
        return usable();
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

    /** Whether renderString may draw this from a list. */
    public boolean cacheable(String text) {
        if (text == null || text.isEmpty() || text.length() > 256 || !usable()) return false;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '§') continue;
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'k' || code == 'n' || code == 'm') return false;
        }
        return true;
    }

    /**
     * renderString through the cache: the list's end position, or NaN to draw it normally this time (first sight). {@code draw}
     * renders the text at the origin; the list is compiled while it runs.
     */
    public float render(String text, float x, float y, int color, boolean shadow, Draw draw) {
        checkFlags();
        Entry entry = lists.get(probe.set(text, color, shadow));
        if (entry == null) {
            if (lists.size() >= MAX_LISTS) clearLists();
            lists.put(new Key().set(text, color, shadow), new Entry());
            return Float.NaN; // drawn normally: its textures get loaded outside a list
        }
        entry.used = tick;
        if (entry.list < 0) return Float.NaN;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        if (entry.list == 0) {
            int list = GLAllocation.generateDisplayLists(1);
            // GlStateManager skips a colour or texture it thinks is set: make the list set both itself.
            GlStateManager.resetColor();
            GlStateManager.bindTexture(0);
            GL11.glGetError(); // an earlier error must not count as this list's
            GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            entry.advance = draw.ladsDrawAtOrigin(text, color, shadow);
            GL11.glEndList();
            entry.texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            // A list that failed to compile (another list was being compiled) is never used.
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                GLAllocation.deleteDisplayLists(list);
                entry.list = -1;
            } else entry.list = list;
        } else {
            GlStateManager.callList(entry.list);
        }
        GlStateManager.popMatrix();
        // The list left its own colour and texture set.
        GlStateManager.resetColor();
        GlStateManager.bindTexture(entry.texture);
        return entry.advance;
    }

    /** renderString itself, drawing at (0, 0): its end x. */
    public interface Draw {
        float ladsDrawAtOrigin(String text, int color, boolean shadow);
    }

    private void checkFlags() {
        if (font.getBidiFlag() == bidi) return;
        bidi = font.getBidiFlag();
        clear();
    }

    /** New font textures and widths (resource reload). */
    public void clear() {
        widths.clear();
        clearLists();
        epoch = new Object();
    }

    private void clearLists() {
        for (Entry entry : lists.values()) if (entry.list > 0) GLAllocation.deleteDisplayLists(entry.list);
        lists.clear();
    }

    /** Client tick: frees what was not drawn for 5 seconds. */
    public static void tick() {
        if (++tick % 20 != 0) return;
        synchronized (ALL) {
            for (FontCache189 cache : ALL) {
                for (Iterator<Entry> it = cache.lists.values().iterator(); it.hasNext(); ) {
                    Entry entry = it.next();
                    if (tick - entry.used <= UNUSED_TICKS) continue;
                    if (entry.list > 0) GLAllocation.deleteDisplayLists(entry.list);
                    it.remove();
                }
            }
        }
    }

    private static final class Entry {
        /** 0 seen once, -1 never cached, else the display list. */
        int list, texture, used = tick;
        float advance;
    }

    private static final class Key {
        String text;
        int color, hash;
        boolean shadow;

        Key set(String text, int color, boolean shadow) {
            this.text = text;
            this.color = color;
            this.shadow = shadow;
            hash = (text.hashCode() * 31 + color) * 2 + (shadow ? 1 : 0);
            return this;
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return key.color == color && key.shadow == shadow && key.text.equals(text);
        }
    }
}
