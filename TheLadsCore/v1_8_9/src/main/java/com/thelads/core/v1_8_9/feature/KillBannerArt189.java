package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.killbanner.KillBannerFeed;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.modules.KillBannerModule;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 26.x KillBannerArt on 1.8.9: Reaver's and Rogue's recoloured frames (KillBannerPlayer.Frame) or a Kingdom Archives skin's
 * layers in motion (KillBannerPlayer.Layers) where Valorant puts them, with their overlays, as DynamicTextures (linearly
 * sampled: the banner is drawn at fractional scales) on Tessellator quads. Art is let go a minute after it was last drawn.
 * <p>Nothing here decodes on the render thread while a banner plays: warm() has a worker read the strips and decode the PNGs, thumbnails
 * and recoloured way-out layers, which are uploaded a few a tick; the strip frames come recoloured from a KillBannerFeed and are
 * uploaded as they are with one glTexSubImage2D.
 */
final class KillBannerArt189 {
    static final class Sprite {
        final DynamicTexture texture;
        final int width, height;
        long used;
        Sprite(DynamicTexture texture, int width, int height) { this.texture = texture; this.width = width; this.height = height; }
    }
    /** Straight pixels ready for a texture, made off the render thread. */
    private static final class Pixels {
        final int[] argb;
        final int width, height;
        int[] box;
        Pixels(int[] argb, int width, int height) { this.argb = argb; this.width = width; this.height = height; }
    }
    /** What an animated skin keeps in use (let go together, a minute after it was last drawn): its frame texture and what shows in it, the feed, way-out layers and thumbnails. */
    private static final class Art {
        final KillBannerStyle style;
        final Sprite[] exit, thumb; // exit: [(kills - 1) * variants + variant] * 2 + (icon ? 1 : 0)
        int[] box;
        long used;
        boolean freed;
        int prepared = -1;
        Sprite live;
        KillBannerFeed feed;
        KillBannerStrip shownStrip;
        int shownVariant = -1, shownFrame = -1;
        Art(KillBannerStyle style) {
            this.style = style;
            int variants = style.variants == null ? 1 : style.variants.length;
            exit = new Sprite[5 * variants * 2];
            thumb = new Sprite[variants];
        }
        int variant(int variant) { return Math.max(0, Math.min(thumb.length - 1, variant)); }
        int exitSlot(int kills, int variant, boolean icon) { return ((Math.min(5, kills) - 1) * thumb.length + variant(variant)) * 2 + (icon ? 1 : 0); }
        void free() {
            freed = true;
            if (live != null) live.texture.deleteGlTexture();
            if (feed != null) feed.release();
            for (Sprite s : exit) if (s != null) s.texture.deleteGlTexture();
            for (Sprite s : thumb) if (s != null) s.texture.deleteGlTexture();
            style.release();
        }
    }
    private static final String DIR = "/assets/theladscore/killbanner/", GLOW = DIR + "glow.png", SHADOW = DIR + "shadow.png",
        MARK = DIR + "mark.png", MARK_THIN = DIR + "mark_thin.png", HEADSHOT = DIR + "headshot.png", HS_MARK = DIR + "hs_mark.png";
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<KillBannerStyle, Art> ART = new EnumMap<>(KillBannerStyle.class);
    /** Sprites asked of the worker and not stored yet (a path that failed stays: it is not asked again, the first draw says why). */
    private static final Set<String> PENDING = new HashSet<>();
    /** What the worker finished, for the client thread to turn into textures. */
    private static final Queue<Runnable> READY = new ConcurrentLinkedQueue<>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Lads kill banner art");
        thread.setDaemon(true);
        return thread;
    });
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore");
    /** Textures made a client tick from what the worker finished. */
    private static final int UPLOADS_A_TICK = 3;
    private static final int[] PHASEGUARD_COLORS = { 0xED6D3B, 0x008BBD, 0x68BD42, 0xD6D642 };
    private static final long IDLE = 60_000_000_000L;
    /** One spray particle (KillBannerPlayer.particle). */
    private static final float[] PARTICLE = new float[6];
    static final String BASE = "/assets/theladscore/textures/gui/base_kill_banner.png";
    private static long now, swept;
    private static ByteBuffer upload;
    /** QA: the strip frame wanted was not ready at the last draw (the previous one stays up). */
    private static boolean stale;

    private KillBannerArt189() {}

    /** Reaver's or Rogue's frame at Valorant's place and size: the ring centre at 79.4% of the screen height, 1.15 px a cell pixel at 1080p. */
    static void draw(int guiWidth, int guiHeight, KillBannerStyle style, int variant, int kills, KillBannerStrip strip, KillBannerPlayer.Frame f, float size) {
        strip(style, variant, kills, strip, f, guiWidth / 2f, guiHeight * .794f, guiHeight * KillBannerStyle.SCREEN_SCALE / 1080f * size);
    }

    /** A Kingdom Archives skin's layers, at the same place and size. */
    static void draw(int guiWidth, int guiHeight, KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l, float size) {
        still(style, variant, kills, l, guiWidth / 2f, guiHeight * .794f, guiHeight * KillBannerStyle.SCREEN_SCALE / 1080f * size);
    }

    /** The banner with its ring centre at x, y and k GUI pixels a cell pixel. */
    private static void strip(KillBannerStyle style, int variant, int kills, KillBannerStrip strip, KillBannerPlayer.Frame f, float x, float y, float k) {
        now = System.nanoTime();
        stale = false;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(k, k, 1.0F);
            GlStateManager.translate(-style.anchorX, -style.anchorY, 0.0F);
            if (f.shadowAlpha() > 0) {
                Sprite shadow = sprite(SHADOW);
                float d = style.ring * 3.7f;
                quad(shadow, style.anchorX - d / 2, style.anchorY + f.iconY() - d / 2, d / shadow.width, argb(0, f.shadowAlpha()));
            }
            if (f.drawnExit()) {
                quad(exitLayer(style, kills, variant, strip, false), 0, 0, 1, argb(0xFFFFFF, f.restAlpha()));
                if (f.iconAlpha() > 0) iconLayer(style, exitLayer(style, kills, variant, strip, true), f.iconScale(), argb(0xFFFFFF, f.iconAlpha()));
            } else {
                Sprite cell = frame(style, variant, strip, f.stripFrame());
                if (cell != null) quad(cell, 0, 0, 1, -1);
            }
            GlStateManager.translate(0.0F, f.iconY(), 0.0F); // the overlays sit on the icon, wherever the strip has it
            if (f.heartAlpha() > 0) iconLayer(style, sprite(style.heartAsset()), f.iconScale(), argb(0xFFFFFF, f.heartAlpha()));
            if (f.strobe() > 0) iconLayer(style, sprite(style.tintAsset()), f.iconScale(), argb(KillBannerPlayer.STROBE_RED & 0xFFFFFF, f.strobe()));
            if (f.markSize() > 0) {
                float cx = style.anchorX, cy = style.anchorY + style.markY * f.iconScale();
                mark(sprite(MARK_THIN), cx, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                mark(sprite(MARK), cx, cy, f.markSize(), f.markColor(), f.markAlpha());
            }
            if (f.labelAlpha() > 0) {
                Sprite label = sprite(HEADSHOT);
                float s = style.ring * 1.45f / label.width;
                quad(label, style.anchorX - label.width * s / 2, style.anchorY + style.labelY, s, argb(0xFFFFFF, f.labelAlpha()));
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** As 26.x KillBannerArt.still: a Kingdom Archives skin's layers in motion with its ring centre at x, y, k GUI pixels a cell pixel. */
    private static void still(KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l, float x, float y, float k) {
        now = System.nanoTime();
        stale = false;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(k * KillBannerStyle.ART_SCALE, k * KillBannerStyle.ART_SCALE, 1.0F);
            int accent = style.accent(variant), shade = grey(l.emblemShade());
            float r = style.ring, ey = l.emblemY();
            if (l.shadowAlpha() > 0) centred(sprite(SHADOW), 0, 0, r * 3.7f / sprite(SHADOW).width, argb(0, l.shadowAlpha()));
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                float s = 1.25f * l.emblemScale();
                if (l.tier() < 1) centred(sprite(style.swapAsset(kills - 1)), 0, ey, s, argb(shade, l.emblemAlpha() * (1 - l.tier())));
                centred(sprite(style.swapAsset(kills)), 0, ey, s, argb(shade, l.emblemAlpha() * l.tier()));
            } else {
                if (style.hasFrame) centred(sprite(style.frameAsset()), 0, 0, l.frameScale(), argb(0xFFFFFF, l.frameAlpha()));
                if (style.hasRing) centred(sprite(style.ringAsset()), 0, 0, l.ringScale(), argb(0xFFFFFF, l.ringAlpha()));
                if (style.hasEmblem) {
                    int base = style.type == KillBannerStyle.Type.PHASEGUARD ? PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))] : 0xFFFFFF;
                    int color = multiply(mix(base, KillBannerPlayer.STROBE_RED & 0xFFFFFF, l.strobe()), shade);
                    centred(sprite(style.emblemAsset(variant)), 0, ey, l.emblemScale(), argb(color, l.emblemAlpha()));
                }
                if (style.hasPip && l.pipAlpha() > 0) {
                    Sprite pip = sprite(style.pipAsset(variant));
                    int count = Math.max(1, Math.min(6, kills));
                    for (int i = 0; i < count; i++) {
                        float deg = 360f / count * (i + 1) + (count == 2 ? 90 : 0) + l.pipSpin(), rad = (float) Math.toRadians(deg);
                        float px = -r * l.pipRadius() * (float) Math.sin(rad), py = -r * l.pipRadius() * (float) Math.cos(rad);
                        if (l.pipFlare() > 0) glow(px, py, pip.width * 2.4f, accent, l.pipFlare());
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(px, py, 0.0F);
                        GlStateManager.rotate(-deg, 0.0F, 0.0F, 1.0F);
                        centred(pip, 0, 0, 1, argb(0xFFFFFF, l.pipAlpha()));
                        GlStateManager.popMatrix();
                    }
                }
            }
            if (l.spray() >= 0) {
                Sprite dot = sprite(GLOW);
                for (int i = 0, n = KillBannerPlayer.sprayCount(kills); i < n; i++) {
                    if (!KillBannerPlayer.particle(i, l.spray(), PARTICLE)) continue;
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(PARTICLE[0] * r, PARTICLE[1] * r + ey, 0.0F);
                    GlStateManager.rotate((float) Math.toDegrees(PARTICLE[5]), 0.0F, 0.0F, 1.0F);
                    GlStateManager.scale(PARTICLE[2] * r * 2 / dot.width, PARTICLE[3] * r * 2 / dot.height, 1.0F);
                    centred(dot, 0, 0, 1, argb(accent, PARTICLE[4]));
                    GlStateManager.popMatrix();
                }
            }
            if (l.markSize() > 0) {
                float cy = style.markY * l.emblemScale() + ey;
                mark(sprite(MARK_THIN), 0, cy, l.markSize(), l.markColor(), l.markThinAlpha());
                mark(sprite(MARK), 0, cy, l.markSize(), l.markColor(), l.markAlpha());
            }
            if (l.labelAlpha() > 0) {
                Sprite hsMark = sprite(HS_MARK);
                quad(hsMark, -hsMark.width / 2f, style.markY * l.emblemScale() + ey - hsMark.height / 2f, 1f,
                    argb(KillBannerPlayer.MARK_RED & 0xFFFFFF, l.labelAlpha()));
                Sprite label = sprite(HEADSHOT);
                float s = style.ring * 1.45f / label.width;
                quad(label, -label.width * s / 2f, style.labelY, s, argb(0xFFFFFF, l.labelAlpha()));
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** The settings preview: the skin's banners for 1 to 5 kills in turn ({@code clock} seconds), fitted into the box. */
    static void preview(KillBannerStyle style, int variant, int x, int y, int w, int h, double clock) {
        int kills = KillBannerPlayer.previewKills(clock);
        double age = KillBannerPlayer.previewAge(clock), seconds = KillBannerPlayer.previewSeconds(kills);
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(kills);
            KillBannerPlayer.Frame f = KillBannerPlayer.at(style, strip, age, seconds, false);
            if (f == null) return;
            float k = Math.min(w / (float) strip.width, h / (float) strip.height);
            strip(style, variant, kills, strip, f, x + w / 2f + (style.anchorX - strip.width / 2f) * k, y + h / 2f + (style.anchorY - strip.height / 2f) * k, k);
            return;
        }
        KillBannerPlayer.Layers l = KillBannerPlayer.layers(style, kills, age, seconds, false);
        if (l == null) return;
        float extent;
        if (style.type == KillBannerStyle.Type.BANNER_SWAP) extent = 1.25f * sprite(style.swapAsset(1)).width;
        else if (style.hasFrame) extent = Math.max(sprite(style.frameAsset()).width, sprite(style.frameAsset()).height);
        else extent = style.ring * 2.6f;
        still(style, variant, kills, l, x + w / 2f, y + h / 2f, Math.min(w, h) * .9f / (extent * KillBannerStyle.ART_SCALE));
    }

    /** Kill Banner picker art: the skin's settled one-kill frame in a variant, with its emblem and kill mark, cropped and fitted into the box. */
    static void thumb(KillBannerStyle style, int variant, int x, int y, int w, int h) {
        now = System.nanoTime();
        if (style.isAnimated()) {
            Art art = art(style);
            int v = art.variant(variant);
            Sprite cell = art.thumb[v];
            if (cell == null) { // not the chosen skin's (warm() has that one): made now
                Pixels pixels = thumbPixels(style, variant);
                art.thumb[v] = cell = texture(pixels);
                art.box = pixels.box;
            }
            cell.used = now;
            int[] box = art.box;
            float k = Math.min(w / (float) box[2], h / (float) box[3]);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + (w - box[2] * k) / 2, y + (h - box[3] * k) / 2, 0.0F);
                GlStateManager.scale(k, k, 1.0F);
                GlStateManager.translate(-box[0], -box[1], 0.0F);
                quad(cell, 0, 0, 1, -1);
                if (style.heart) iconLayer(style, sprite(style.heartAsset()), 1, -1);
                mark(sprite(MARK), style.anchorX, style.anchorY + style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1);
            } finally {
                GlStateManager.popMatrix();
            }
            return;
        }

        if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
            Sprite swap = sprite(style.swapAsset(1));
            float sw = swap.width * 1.25f, sh = swap.height * 1.25f;
            float k = Math.min(w / sw, h / sh);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x + w / 2f, y + h / 2f, 0.0F);
                GlStateManager.scale(k, k, 1.0F);
                quad(swap, -swap.width * 1.25f / 2f, -swap.height * 1.25f / 2f, 1.25f, -1);
            } finally {
                GlStateManager.popMatrix();
            }
            return;
        }

        Sprite frame = style.hasFrame ? sprite(style.frameAsset()) : null;
        float bw = frame != null ? frame.width : style.ring * 2.5f;
        float bh = frame != null ? frame.height : style.ring * 2.5f;
        float k = Math.min(w / bw, h / bh);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + w / 2f, y + h / 2f, 0.0F);
            GlStateManager.scale(k, k, 1.0F);
            if (frame != null) quad(frame, -frame.width / 2f, -frame.height / 2f, 1f, -1);
            if (style.hasRing) {
                Sprite ring = sprite(style.ringAsset());
                quad(ring, -ring.width / 2f, -ring.height / 2f, 1f, -1);
            }
            if (style.hasEmblem) {
                Sprite emblem = sprite(style.emblemAsset(variant));
                int tint = style.type == KillBannerStyle.Type.PHASEGUARD
                    ? argb(PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))], 1f)
                    : -1;
                quad(emblem, -emblem.width / 2f, -emblem.height / 2f, 1f, tint);
            }
            if (style.hasPip) {
                Sprite pip = sprite(style.pipAsset(variant));
                quad(pip, -pip.width / 2f, -style.ring - pip.height / 2f, 1f, -1);
            }
            mark(sprite(MARK), 0, style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1f);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /**
     * Gets the skin's art ready (each client tick for the chosen skin, and for the base banner see warmBase), so its first kill does
     * not wait for it and it is kept: a worker reads the strips and decodes the PNGs, the sprites it finishes become textures here,
     * a few a tick. Art is asked for once and is not asked for again while it is loading.
     */
    static void warm(KillBannerStyle style, int variant) {
        now = System.nanoTime();
        poll();
        for (String path : plan(style, variant)) request(path);
        if (!style.isAnimated()) return;
        Art art = art(style);
        for (Sprite s : art.exit) if (s != null) s.used = now;
        for (Sprite s : art.thumb) if (s != null) s.used = now;
        if (art.prepared != variant) {
            art.prepared = variant;
            prepare(art, variant);
        }
    }

    /** The base banner's one sprite, ready ahead of the first kill. */
    static void warmBase() {
        now = System.nanoTime();
        poll();
        request(BASE);
    }

    /**
     * A kill of this look has just been seen: points the feed at its strip and has the worker start decoding, so the frames are
     * there when the first one is drawn (if the strip is not read yet, the first draw reads it).
     */
    static void prefetch(KillBannerModule.Pick pick, int kills) {
        KillBannerStyle style = pick == null ? null : pick.style();
        if (style == null || !style.isAnimated()) return;
        KillBannerStrip strip = style.loadedStrip(kills);
        if (strip == null) return;
        now = System.nanoTime();
        Art art = art(style);
        if (art.feed == null) art.feed = new KillBannerFeed(style);
        art.feed.target(strip, pick.variant());
        art.feed.get(0, 0);
    }

    /** QA: false once the strip frame the last draw wanted was the one drawn. */
    static boolean stale() {
        return stale;
    }

    /** QA: animated skins holding art (frame texture, feed, thumbnails, way-out layers, strips). */
    static int held() {
        return ART.size();
    }

    /** The sprites a skin's banner draws, for a variant (built once). */
    private static final Map<KillBannerStyle, String[][]> PLANS = new EnumMap<>(KillBannerStyle.class);

    private static String[] plan(KillBannerStyle style, int variant) {
        String[][] byVariant = PLANS.get(style);
        if (byVariant == null) PLANS.put(style, byVariant = new String[style.variantNames.length][]);
        int v = Math.max(0, Math.min(byVariant.length - 1, variant));
        if (byVariant[v] != null) return byVariant[v];
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add(SHADOW);
        paths.add(MARK);
        paths.add(MARK_THIN);
        paths.add(HEADSHOT);
        if (style.isAnimated()) {
            paths.add(style.tintAsset());
            if (style.heart) paths.add(style.heartAsset());
        } else {
            paths.add(GLOW);
            paths.add(HS_MARK);
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                for (int kills = 1; kills <= 5; kills++) paths.add(style.swapAsset(kills));
            } else {
                if (style.hasFrame) paths.add(style.frameAsset());
                if (style.hasRing) paths.add(style.ringAsset());
                if (style.hasEmblem) paths.add(style.emblemAsset(v));
                if (style.hasPip) paths.add(style.pipAsset(v));
            }
        }
        return byVariant[v] = paths.toArray(new String[0]);
    }

    /** Lets go of art not drawn for a minute; looks every ten seconds (each client tick calls it). */
    static void sweep() {
        long time = System.nanoTime();
        if (time - swept < 10_000_000_000L) return;
        swept = time;
        for (Iterator<Sprite> it = SPRITES.values().iterator(); it.hasNext(); ) {
            Sprite s = it.next();
            if (time - s.used < IDLE) continue;
            s.texture.deleteGlTexture();
            it.remove();
        }
        for (Iterator<Art> it = ART.values().iterator(); it.hasNext(); ) {
            Art art = it.next();
            if (time - art.used < IDLE) continue;
            art.free(); // the frame texture, feed, way-out layers, thumbnails and the strips with their inflaters
            it.remove();
        }
        if (ART.isEmpty()) upload = null;
    }

    /** Textures from what the worker has finished (a few a tick, each is a texture upload). */
    private static void poll() {
        Runnable ready;
        for (int i = 0; i < UPLOADS_A_TICK && (ready = READY.poll()) != null; i++) ready.run();
    }

    private static Art art(KillBannerStyle style) {
        Art art = ART.get(style);
        if (art == null) ART.put(style, art = new Art(style));
        art.used = now;
        return art;
    }

    /** Has the worker decode a sprite unless it is loaded or on its way. */
    private static void request(String path) {
        Sprite sprite = SPRITES.get(path);
        if (sprite != null) {
            sprite.used = now;
            return;
        }
        if (!PENDING.add(path)) return;
        LOADER.execute(() -> {
            try {
                Pixels pixels = decode(path);
                READY.add(() -> {
                    PENDING.remove(path);
                    if (!SPRITES.containsKey(path)) SPRITES.put(path, texture(pixels)); // a draw may have loaded it first
                });
            } catch (Throwable failure) {
                LOGGER.warn("Lads kill banner art not loaded ahead: {}", failure.toString());
            }
        });
    }

    /** Worker: an animated skin's strips, the chosen variant's thumbnail and its way-out layers. */
    private static void prepare(Art art, int variant) {
        KillBannerStyle style = art.style;
        LOADER.execute(() -> {
            try {
                KillBannerStrip first = style.strip(1);
                for (int kills = 2; kills <= 5; kills++) style.strip(kills);
                Pixels thumb = thumbPixels(style, variant);
                READY.add(() -> {
                    if (art.freed) return;
                    if (art.live == null) {
                        art.live = liveTexture(first.width, first.height);
                        write(art.live, new byte[first.width * first.height * 4]); // the upload buffer and the GL path are warm before the first kill
                    }
                    int v = art.variant(variant);
                    if (art.thumb[v] == null) art.thumb[v] = texture(thumb);
                    art.box = thumb.box;
                });
                for (int kills = 1; kills <= 5; kills++) {
                    KillBannerStrip strip = style.strip(kills);
                    if (strip.exitFrames != 0) continue; // Rogue's way out is in its frames
                    for (int part = 0; part < 2; part++) {
                        boolean icon = part == 1;
                        int slot = art.exitSlot(kills, variant, icon);
                        Pixels pixels = exitPixels(style, strip, variant, icon);
                        READY.add(() -> {
                            if (!art.freed && art.exit[slot] == null) art.exit[slot] = texture(pixels);
                        });
                    }
                }
            } catch (Throwable failure) {
                LOGGER.warn("Lads kill banner {} not prepared ahead: {}", style.id, failure.toString());
            }
        });
    }

    /** x, y, width and height of the frame's visible pixels. */
    private static int[] bounds(byte[] rgba, int w, int h) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if ((rgba[(y * w + x) * 4 + 3] & 255) > 24) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
        return maxX < 0 ? new int[] {0, 0, w, h} : new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    static int argb(int rgb, float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | rgb & 0xFFFFFF;
    }

    private static int grey(float value) {
        int v = Math.round(Math.max(0, Math.min(1, value)) * 255);
        return v << 16 | v << 8 | v;
    }

    private static int mix(int a, int b, float t) {
        float inv = 1 - t;
        int r = Math.round(((a >> 16) & 255) * inv + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * inv + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * inv + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int multiply(int a, int b) {
        return ((a >> 16 & 255) * (b >> 16 & 255) / 255) << 16 | ((a >> 8 & 255) * (b >> 8 & 255) / 255) << 8 | (a & 255) * (b & 255) / 255;
    }

    /** Blending on, alpha test off (the fades go below its 0.1), white; end() puts 1.8.9's GUI state back. */
    static void begin() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
    }

    static void end() {
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The whole sprite at x, y, w by h, tinted ARGB. */
    static void blit(Sprite s, float x, float y, float w, float h, int argb) {
        GlStateManager.bindTexture(s.texture.getGlTextureId());
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + h, 0).tex(0, 1).endVertex();
        buffer.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        buffer.pos(x + w, y, 0).tex(1, 0).endVertex();
        buffer.pos(x, y, 0).tex(0, 0).endVertex();
        tessellator.draw();
    }

    private static void quad(Sprite s, float x, float y, float scale, int color) {
        blit(s, x, y, s.width * scale, s.height * scale, color);
    }

    /** The sprite centred on x, y. */
    private static void centred(Sprite s, float x, float y, float scale, int color) {
        if ((color >>> 24) == 0) return;
        quad(s, x - s.width * scale / 2, y - s.height * scale / 2, scale, color);
    }

    /** A soft dot of light {@code size} across, centred on x, y. */
    private static void glow(float x, float y, float size, int rgb, float alpha) {
        Sprite dot = sprite(GLOW);
        centred(dot, x, y, size / dot.width, argb(rgb, alpha));
    }

    /** A full-cell layer of the icon, scaled about the ring centre (the icon's centre) as the icon leaves. */
    private static void iconLayer(KillBannerStyle style, Sprite s, float scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(style.anchorX, style.anchorY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-style.anchorX, -style.anchorY, 0.0F);
        quad(s, 0, 0, 1, color);
        GlStateManager.popMatrix();
    }

    private static void mark(Sprite s, float cx, float cy, float size, int color, float alpha) {
        if (alpha <= 0) return;
        quad(s, cx - size / 2, cy - size / 2, size / s.width, argb(color, alpha));
    }

    static Sprite sprite(String path) {
        Sprite sprite = SPRITES.get(path);
        if (sprite == null) {
            SPRITES.put(path, sprite = texture(decode(path)));
        }
        sprite.used = now;
        return sprite;
    }

    /** A PNG of the mod's assets as texture pixels. */
    private static Pixels decode(String path) {
        try (InputStream in = KillBannerArt189.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException(path + " is missing");
            BufferedImage image = ImageIO.read(in);
            int w = image.getWidth(), h = image.getHeight();
            return new Pixels(image.getRGB(0, 0, w, h, null, 0, w), w, h);
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner art unavailable: " + path, failure);
        }
    }

    /** Reaver's settled icon or the rest of its banner, recoloured, for the drawn way out. */
    private static Sprite exitLayer(KillBannerStyle style, int kills, int variant, KillBannerStrip strip, boolean icon) {
        Art art = art(style);
        int slot = art.exitSlot(kills, variant, icon);
        Sprite sprite = art.exit[slot];
        if (sprite == null) art.exit[slot] = sprite = texture(exitPixels(style, strip, variant, icon)); // warm() has not got here yet
        sprite.used = now;
        return sprite;
    }

    private static Pixels exitPixels(KillBannerStyle style, KillBannerStrip strip, int variant, boolean icon) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(icon ? strip.exitIcon : strip.exitRest));
            int w = image.getWidth(), h = image.getHeight();
            byte[] rgba = new byte[w * h * 4];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int c = image.getRGB(x, y), o = (y * w + x) * 4;
                rgba[o] = (byte) (c >> 16); rgba[o + 1] = (byte) (c >> 8); rgba[o + 2] = (byte) c; rgba[o + 3] = (byte) (c >>> 24);
            }
            style.recolor(rgba, variant);
            return pack(rgba, w, h);
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner way-out art unavailable: " + style.id + (icon ? "/icon" : "/rest"), failure);
        }
    }

    /** The picker's settled one-kill frame in a variant, with the box of its visible pixels. */
    private static Pixels thumbPixels(KillBannerStyle style, int variant) {
        KillBannerStrip strip = style.strip(1);
        byte[] rgba = new byte[strip.width * strip.height * 4];
        strip.frame(strip.introEnd, rgba);
        int[] box = bounds(rgba, strip.width, strip.height);
        style.recolor(rgba, variant);
        Pixels pixels = pack(rgba, strip.width, strip.height);
        pixels.box = box;
        return pixels;
    }

    /** The style's frame texture with the strip frame in it: the feed's recoloured pixels, uploaded when the frame changes. */
    private static Sprite frame(KillBannerStyle style, int variant, KillBannerStrip strip, int index) {
        Art art = art(style);
        index = Math.max(0, Math.min(strip.frames - 1, index));
        if (art.live == null) art.live = liveTexture(strip.width, strip.height); // one cell size a style
        if (art.feed == null) art.feed = new KillBannerFeed(style);
        art.feed.target(strip, variant);
        if (art.shownStrip != strip || art.shownVariant != variant || art.shownFrame != index) {
            // The render thread never waits for the worker: a frame not decoded yet leaves the last one up (or none, at a banner's very start).
            byte[] pixels = art.feed.get(index, 0);
            if (pixels != null) {
                write(art.live, pixels);
                art.shownStrip = strip;
                art.shownVariant = variant;
                art.shownFrame = index;
            } else {
                stale = true;
                if (art.shownStrip != strip || art.shownVariant != variant) return null; // nothing of this banner to show yet
            }
        }
        return art.live;
    }

    private static Sprite liveTexture(int w, int h) {
        return linear(new DynamicTexture(w, h), w, h);
    }

    private static Sprite texture(Pixels pixels) {
        DynamicTexture texture = new DynamicTexture(pixels.width, pixels.height);
        System.arraycopy(pixels.argb, 0, texture.getTextureData(), 0, pixels.argb.length);
        texture.updateDynamicTexture();
        return linear(texture, pixels.width, pixels.height);
    }

    /** Straight RGBA bytes as the texture's ARGB pixels. */
    private static Pixels pack(byte[] rgba, int w, int h) {
        int[] argb = new int[w * h];
        for (int i = 0, o = 0; i < argb.length; i++, o += 4)
            argb[i] = (rgba[o + 3] & 255) << 24 | (rgba[o] & 255) << 16 | (rgba[o + 1] & 255) << 8 | rgba[o + 2] & 255;
        return new Pixels(argb, w, h);
    }

    /** Straight RGBA bytes into the whole texture, as they are (the filtering set at its making stays). */
    private static void write(Sprite cell, byte[] rgba) {
        int size = cell.width * cell.height * 4;
        if (upload == null || upload.capacity() < size) upload = BufferUtils.createByteBuffer(size);
        upload.clear();
        upload.put(rgba, 0, size);
        upload.flip();
        GlStateManager.bindTexture(cell.texture.getGlTextureId());
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, cell.width, cell.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
    }

    /** 1.8.9's upload leaves nearest sampling and repeat: the banner wants linear and clamped edges. */
    private static Sprite linear(DynamicTexture texture, int w, int h) {
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return new Sprite(texture, w, h);
    }
}
