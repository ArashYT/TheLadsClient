package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws a skin banner where Valorant puts it: Reaver's and Rogue's recoloured frames (KillBannerPlayer.Frame) or a Kingdom
 * Archives skin's layers in motion (KillBannerPlayer.Layers), with their overlays. Art is loaded once and let go a minute
 * after it was last drawn (the picker can show every skin's).
 */
final class KillBannerArt {
    /** A loaded texture and when it was last drawn. */
    private static final class Sprite {
        final Identifier id;
        final int width, height;
        long used;
        Sprite(Identifier id, int width, int height) { this.id = id; this.width = width; this.height = height; }
        Identifier id() { return id; }
        int width() { return width; }
        int height() { return height; }
    }
    private static final class Live {
        DynamicTexture texture;
        Identifier id;
        KillBannerStrip strip;
        int frame = -1, variant = -1;
        byte[] copy;
        long used;
    }
    private static final String DIR = "/assets/theladscore/killbanner/", GLOW = DIR + "glow.png", SHADOW = DIR + "shadow.png",
        MARK = DIR + "mark.png", MARK_THIN = DIR + "mark_thin.png", HEADSHOT = DIR + "headshot.png", HS_MARK = DIR + "hs_mark.png";
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<KillBannerStyle, Live> LIVE = new EnumMap<>(KillBannerStyle.class);
    private static final Map<KillBannerStyle, int[]> BOUNDS = new EnumMap<>(KillBannerStyle.class);
    private static final int[] PHASEGUARD_COLORS = { 0xED6D3B, 0x008BBD, 0x68BD42, 0xD6D642 };
    private static final long IDLE = 60_000_000_000L;
    /** One spray particle (KillBannerPlayer.particle). */
    private static final float[] PARTICLE = new float[6];
    private static long now, swept, warmed;

    private KillBannerArt() {}

    /** Reaver's or Rogue's frame at Valorant's place and size: the ring centre at 79.4% of the screen height, 1.15 px a cell pixel at 1080p. */
    static void draw(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int kills, KillBannerStrip strip,
                     KillBannerPlayer.Frame f, float size) {
        strip(g, style, variant, kills, strip, f, g.guiWidth() / 2f, g.guiHeight() * .794f, scale(g, size));
    }

    /** A Kingdom Archives skin's layers, at the same place and size. */
    static void draw(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l, float size) {
        still(g, style, variant, kills, l, g.guiWidth() / 2f, g.guiHeight() * .794f, scale(g, size));
    }

    private static float scale(GuiGraphicsExtractor g, float size) {
        return g.guiHeight() * KillBannerStyle.SCREEN_SCALE / 1080f * size;
    }

    /** The banner with its ring centre at x, y and k GUI pixels a cell pixel. */
    private static void strip(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int kills, KillBannerStrip strip,
                              KillBannerPlayer.Frame f, float x, float y, float k) {
        now = System.nanoTime();
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(k, k);
            pose.translate(-style.anchorX, -style.anchorY);
            if (f.shadowAlpha() > 0) {
                Sprite shadow = sprite(SHADOW);
                float d = style.ring * 3.7f;
                quad(g, shadow, style.anchorX - d / 2, style.anchorY + f.iconY() - d / 2, d / shadow.width(), argb(0, f.shadowAlpha()));
            }
            if (f.drawnExit()) {
                quad(g, exitLayer(style, kills, variant, strip, false), 0, 0, 1, argb(0xFFFFFF, f.restAlpha()));
                if (f.iconAlpha() > 0)
                    iconLayer(g, style, exitLayer(style, kills, variant, strip, true), f.iconScale(), argb(0xFFFFFF, f.iconAlpha()));
            } else {
                Identifier frame = frame(style, variant, strip, f.stripFrame());
                g.blit(RenderPipelines.GUI_TEXTURED, frame, 0, 0, 0, 0, strip.width, strip.height, strip.width, strip.height, -1);
            }
            pose.translate(0, f.iconY()); // the overlays sit on the icon, wherever the strip has it
            if (f.heartAlpha() > 0) iconLayer(g, style, sprite(style.asset("heart.png")), f.iconScale(), argb(0xFFFFFF, f.heartAlpha()));
            if (f.strobe() > 0) iconLayer(g, style, sprite(style.asset("tint.png")), f.iconScale(),
                argb(KillBannerPlayer.STROBE_RED & 0xFFFFFF, f.strobe()));
            if (f.markSize() > 0) {
                float cx = style.anchorX, cy = style.anchorY + style.markY * f.iconScale();
                mark(g, sprite(MARK_THIN), cx, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                mark(g, sprite(MARK), cx, cy, f.markSize(), f.markColor(), f.markAlpha());
            }
            if (f.labelAlpha() > 0) {
                Sprite label = sprite(HEADSHOT);
                float s = style.ring * 1.45f / label.width();
                quad(g, label, style.anchorX - label.width() * s / 2, style.anchorY + style.labelY, s, argb(0xFFFFFF, f.labelAlpha()));
            }
        } finally {
            pose.popMatrix();
        }
    }

    /**
     * A Kingdom Archives skin (its art in its own pixels) with its ring centre at x, y and k GUI pixels a cell pixel: the frame,
     * ring, emblem and a pip for each kill as they move in, the glow, glint and spray in the skin's accent colour, the kill
     * mark and the HEADSHOT label. A Banner Swap skin's art for this kill count turns out of the previous one's.
     */
    private static void still(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l,
                              float x, float y, float k) {
        now = System.nanoTime();
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(k * KillBannerStyle.ART_SCALE, k * KillBannerStyle.ART_SCALE);
            int accent = style.accent(variant), shade = grey(l.emblemShade());
            float r = style.ring, ey = l.emblemY();
            if (l.shadowAlpha() > 0) centred(g, sprite(SHADOW), 0, 0, r * 3.7f / sprite(SHADOW).width(), argb(0, l.shadowAlpha()));
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                float s = 1.25f * l.emblemScale();
                if (l.tier() < 1) centred(g, sprite(style.swapAsset(kills - 1)), 0, ey, s, argb(shade, l.emblemAlpha() * (1 - l.tier())));
                centred(g, sprite(style.swapAsset(kills)), 0, ey, s, argb(shade, l.emblemAlpha() * l.tier()));
            } else {
                if (style.hasFrame) centred(g, sprite(style.frameAsset()), 0, 0, l.frameScale(), argb(0xFFFFFF, l.frameAlpha()));
                if (style.hasRing) centred(g, sprite(style.ringAsset()), 0, 0, l.ringScale(), argb(0xFFFFFF, l.ringAlpha()));
                if (style.hasEmblem) {
                    int base = style.type == KillBannerStyle.Type.PHASEGUARD ? PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))] : 0xFFFFFF;
                    int color = multiply(mix(base, KillBannerPlayer.STROBE_RED & 0xFFFFFF, l.strobe()), shade);
                    centred(g, sprite(style.emblemAsset(variant)), 0, ey, l.emblemScale(), argb(color, l.emblemAlpha()));
                }
                if (style.hasPip && l.pipAlpha() > 0) {
                    Sprite pip = sprite(style.pipAsset(variant));
                    int count = Math.max(1, Math.min(6, kills));
                    for (int i = 0; i < count; i++) {
                        float deg = 360f / count * (i + 1) + (count == 2 ? 90 : 0) + l.pipSpin(), rad = (float) Math.toRadians(deg);
                        float px = -r * l.pipRadius() * (float) Math.sin(rad), py = -r * l.pipRadius() * (float) Math.cos(rad);
                        if (l.pipFlare() > 0) glow(g, px, py, pip.width() * 2.4f, accent, l.pipFlare());
                        pose.pushMatrix();
                        pose.translate(px, py);
                        pose.rotate(-rad);
                        centred(g, pip, 0, 0, 1, argb(0xFFFFFF, l.pipAlpha()));
                        pose.popMatrix();
                    }
                }
            }
            if (l.spray() >= 0) {
                Sprite dot = sprite(GLOW);
                for (int i = 0, n = KillBannerPlayer.sprayCount(kills); i < n; i++) {
                    if (!KillBannerPlayer.particle(i, l.spray(), PARTICLE)) continue;
                    pose.pushMatrix();
                    pose.translate(PARTICLE[0] * r, PARTICLE[1] * r + ey);
                    pose.rotate(PARTICLE[5]);
                    pose.scale(PARTICLE[2] * r * 2 / dot.width(), PARTICLE[3] * r * 2 / dot.height());
                    centred(g, dot, 0, 0, 1, argb(accent, PARTICLE[4]));
                    pose.popMatrix();
                }
            }
            if (l.markSize() > 0) {
                float cy = style.markY * l.emblemScale() + ey;
                mark(g, sprite(MARK_THIN), 0, cy, l.markSize(), l.markColor(), l.markThinAlpha());
                mark(g, sprite(MARK), 0, cy, l.markSize(), l.markColor(), l.markAlpha());
            }
            if (l.labelAlpha() > 0) {
                Sprite hsMark = sprite(HS_MARK);
                quad(g, hsMark, -hsMark.width() / 2f, style.markY * l.emblemScale() + ey - hsMark.height() / 2f, 1f,
                    argb(KillBannerPlayer.MARK_RED & 0xFFFFFF, l.labelAlpha()));
                Sprite label = sprite(HEADSHOT);
                float s = style.ring * 1.45f / label.width();
                quad(g, label, -label.width() * s / 2f, style.labelY, s, argb(0xFFFFFF, l.labelAlpha()));
            }
        } finally {
            pose.popMatrix();
        }
    }

    /** The settings preview: the skin's banners for 1 to 5 kills in turn ({@code clock} seconds), fitted into the box. */
    static void preview(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int x, int y, int w, int h, double clock) {
        int kills = KillBannerPlayer.previewKills(clock);
        double age = KillBannerPlayer.previewAge(clock), seconds = KillBannerPlayer.previewSeconds(kills);
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(kills);
            KillBannerPlayer.Frame f = KillBannerPlayer.at(style, strip, age, seconds, false);
            if (f == null) return;
            float k = Math.min(w / (float) strip.width, h / (float) strip.height);
            strip(g, style, variant, kills, strip, f, x + w / 2f + (style.anchorX - strip.width / 2f) * k,
                y + h / 2f + (style.anchorY - strip.height / 2f) * k, k);
            return;
        }
        KillBannerPlayer.Layers l = KillBannerPlayer.layers(style, kills, age, seconds, false);
        if (l == null) return;
        float extent;
        if (style.type == KillBannerStyle.Type.BANNER_SWAP) extent = 1.25f * sprite(style.swapAsset(1)).width();
        else if (style.hasFrame) extent = Math.max(sprite(style.frameAsset()).width(), sprite(style.frameAsset()).height());
        else extent = style.ring * 2.6f;
        still(g, style, variant, kills, l, x + w / 2f, y + h / 2f, Math.min(w, h) * .9f / (extent * KillBannerStyle.ART_SCALE));
    }

    /** Kill Banner picker art: the skin's settled one-kill frame in a variant, with its emblem and kill mark, cropped and fitted into the box. */
    static void thumb(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int x, int y, int w, int h) {
        now = System.nanoTime();
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(1);
            Sprite cell = cached(style.id + "/thumb/" + variant, key -> {
                byte[] rgba = strip.frame(strip.introEnd).clone();
                style.recolor(rgba, variant);
                NativeImage image = new NativeImage(strip.width, strip.height, true);
                write(image, rgba, strip.width, strip.height);
                return register("killbanner/thumb/" + style.id + "_" + variant, image);
            });
            int[] box = BOUNDS.computeIfAbsent(style, s -> bounds(strip.frame(strip.introEnd), strip.width, strip.height));
            float k = Math.min(w / (float) box[2], h / (float) box[3]);
            var pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(x + (w - box[2] * k) / 2, y + (h - box[3] * k) / 2);
                pose.scale(k, k);
                pose.translate(-box[0], -box[1]);
                quad(g, cell, 0, 0, 1, -1);
                if (style.heart) iconLayer(g, style, sprite(style.asset("heart.png")), 1, -1);
                mark(g, sprite(MARK), style.anchorX, style.anchorY + style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1);
            } finally {
                pose.popMatrix();
            }
            return;
        }

        if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
            Sprite swap = sprite(style.swapAsset(1));
            float sw = swap.width() * 1.25f, sh = swap.height() * 1.25f;
            float k = Math.min(w / sw, h / sh);
            var pose = g.pose();
            pose.pushMatrix();
            try {
                pose.translate(x + w / 2f, y + h / 2f);
                pose.scale(k, k);
                quad(g, swap, -swap.width() * 1.25f / 2f, -swap.height() * 1.25f / 2f, 1.25f, -1);
            } finally {
                pose.popMatrix();
            }
            return;
        }

        Sprite frame = style.hasFrame ? sprite(style.frameAsset()) : null;
        float bw = frame != null ? frame.width() : style.ring * 2.5f;
        float bh = frame != null ? frame.height() : style.ring * 2.5f;
        float k = Math.min(w / bw, h / bh);
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x + w / 2f, y + h / 2f);
            pose.scale(k, k);
            if (frame != null) quad(g, frame, -frame.width() / 2f, -frame.height() / 2f, 1f, -1);
            if (style.hasRing) {
                Sprite ring = sprite(style.ringAsset());
                quad(g, ring, -ring.width() / 2f, -ring.height() / 2f, 1f, -1);
            }
            if (style.hasEmblem) {
                Sprite emblem = sprite(style.emblemAsset(variant));
                int tint = style.type == KillBannerStyle.Type.PHASEGUARD
                    ? argb(PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))], 1f)
                    : -1;
                quad(g, emblem, -emblem.width() / 2f, -emblem.height() / 2f, 1f, tint);
            }
            if (style.hasPip) {
                Sprite pip = sprite(style.pipAsset(variant));
                quad(g, pip, -pip.width() / 2f, -style.ring - pip.height() / 2f, 1f, -1);
            }
            mark(g, sprite(MARK), 0, style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1f);
        } finally {
            pose.popMatrix();
        }
    }

    /**
     * Loads the skin's art now (each client tick for the chosen skin), so its first kill does not wait for it and it is kept;
     * Reaver's and Rogue's frames one kill count a tick.
     */
    static void warm(KillBannerStyle style, int variant) {
        now = System.nanoTime();
        sprite(SHADOW);
        sprite(MARK);
        sprite(MARK_THIN);
        if (style.isAnimated()) {
            sprite(style.asset("tint.png"));
            if (style.heart) sprite(style.asset("heart.png"));
            style.strip(1 + (int) (warmed++ % 5));
            return;
        }
        sprite(GLOW);
        if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
            for (int kills = 1; kills <= 5; kills++) sprite(style.swapAsset(kills));
            return;
        }
        if (style.hasFrame) sprite(style.frameAsset());
        if (style.hasRing) sprite(style.ringAsset());
        if (style.hasEmblem) sprite(style.emblemAsset(variant));
        if (style.hasPip) sprite(style.pipAsset(variant));
    }

    /** Lets go of art not drawn for a minute; looks every ten seconds (each client tick calls it). */
    static void sweep() {
        long time = System.nanoTime();
        if (time - swept < 10_000_000_000L) return;
        swept = time;
        var textures = Minecraft.getInstance().getTextureManager();
        SPRITES.values().removeIf(s -> {
            if (time - s.used < IDLE) return false;
            textures.release(s.id);
            return true;
        });
        // The frame texture, the bounds and the strips (packed frames, inflaters) of a skin not drawn for a minute.
        LIVE.entrySet().removeIf(e -> {
            if (time - e.getValue().used < IDLE) return false;
            textures.release(e.getValue().id);
            BOUNDS.remove(e.getKey());
            e.getKey().release();
            return true;
        });
    }

    /** x, y, width and height of the frame's visible pixels. */
    private static int[] bounds(byte[] rgba, int w, int h) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if ((rgba[(y * w + x) * 4 + 3] & 255) > 24) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
        return maxX < 0 ? new int[] {0, 0, w, h} : new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static int argb(int rgb, float alpha) {
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

    private static void quad(GuiGraphicsExtractor g, Sprite s, float x, float y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, s.id(), 0, 0, 0, 0, s.width(), s.height(), s.width(), s.height(), color);
        pose.popMatrix();
    }

    /** The sprite centred on x, y. */
    private static void centred(GuiGraphicsExtractor g, Sprite s, float x, float y, float scale, int color) {
        if ((color >>> 24) == 0) return;
        quad(g, s, x - s.width() * scale / 2, y - s.height() * scale / 2, scale, color);
    }

    /** A soft dot of light {@code size} across, centred on x, y. */
    private static void glow(GuiGraphicsExtractor g, float x, float y, float size, int rgb, float alpha) {
        Sprite dot = sprite(GLOW);
        centred(g, dot, x, y, size / dot.width(), argb(rgb, alpha));
    }

    /** A full-cell layer of the icon, scaled about the ring centre (the icon's centre) as the icon leaves. */
    private static void iconLayer(GuiGraphicsExtractor g, KillBannerStyle style, Sprite s, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(style.anchorX, style.anchorY);
        pose.scale(scale, scale);
        pose.translate(-style.anchorX, -style.anchorY);
        g.blit(RenderPipelines.GUI_TEXTURED, s.id(), 0, 0, 0, 0, s.width(), s.height(), s.width(), s.height(), color);
        pose.popMatrix();
    }

    private static void mark(GuiGraphicsExtractor g, Sprite s, float cx, float cy, float size, int color, float alpha) {
        if (alpha <= 0) return;
        quad(g, s, cx - size / 2, cy - size / 2, size / s.width(), argb(color, alpha));
    }

    /** The texture for this key, made once and marked drawn. */
    private static Sprite cached(String key, Function<String, Sprite> make) {
        Sprite sprite = SPRITES.computeIfAbsent(key, make);
        sprite.used = now;
        return sprite;
    }

    private static Sprite sprite(String path) {
        return cached(path, p -> {
            try (InputStream in = KillBannerArt.class.getResourceAsStream(p)) {
                if (in == null) throw new IOException(p + " is missing");
                String name = p.substring(p.indexOf("/killbanner/") + 12).replace(".png", "").replace('/', '_');
                return register("killbanner/" + name, NativeImage.read(in));
            } catch (IOException failure) {
                throw new IllegalStateException("Kill banner art unavailable: " + p, failure);
            }
        });
    }

    /** Reaver's settled icon or the rest of its banner, recoloured, for the drawn way out. */
    private static Sprite exitLayer(KillBannerStyle style, int kills, int variant, KillBannerStrip strip, boolean icon) {
        String key = style.id + "/k" + Math.min(5, kills) + "/" + variant + (icon ? "/icon" : "/rest");
        return cached(key, k -> {
            try {
                NativeImage image = NativeImage.read(icon ? strip.exitIcon : strip.exitRest);
                int w = image.getWidth(), h = image.getHeight();
                byte[] rgba = new byte[w * h * 4];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = image.getPixel(x, y), o = (y * w + x) * 4;
                    rgba[o] = (byte) (c >> 16); rgba[o + 1] = (byte) (c >> 8); rgba[o + 2] = (byte) c; rgba[o + 3] = (byte) (c >>> 24);
                }
                style.recolor(rgba, variant);
                write(image, rgba, w, h);
                return register("killbanner/exit/" + key.replace('/', '_'), image);
            } catch (IOException failure) {
                throw new IllegalStateException("Kill banner way-out art unavailable: " + key, failure);
            }
        });
    }

    /** The style's frame texture, rewritten when the frame, the strip or the variant changes. */
    private static Identifier frame(KillBannerStyle style, int variant, KillBannerStrip strip, int index) {
        Live live = LIVE.computeIfAbsent(style, s -> new Live());
        live.used = now;
        if (live.texture == null) { // one cell size a style
            live.copy = new byte[strip.width * strip.height * 4];
            NativeImage image = new NativeImage(strip.width, strip.height, true);
            live.id = Identifier.fromNamespaceAndPath("theladscore", "killbanner/frame_" + style.id);
            live.texture = linear(image);
            Minecraft.getInstance().getTextureManager().register(live.id, live.texture);
            live.frame = -1;
        }
        if (live.strip != strip || live.frame != index || live.variant != variant) {
            System.arraycopy(strip.frame(index), 0, live.copy, 0, live.copy.length);
            style.recolor(live.copy, variant);
            write(live.texture.getPixels(), live.copy, strip.width, strip.height);
            live.texture.upload();
            live.strip = strip;
            live.frame = index;
            live.variant = variant;
        }
        return live.id;
    }

    private static void write(NativeImage image, byte[] rgba, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int o = (y * w + x) * 4;
            image.setPixel(x, y, (rgba[o + 3] & 255) << 24 | (rgba[o] & 255) << 16 | (rgba[o + 1] & 255) << 8 | rgba[o + 2] & 255);
        }
    }

    private static Sprite register(String path, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath("theladscore", path);
        Minecraft.getInstance().getTextureManager().register(id, linear(image));
        return new Sprite(id, image.getWidth(), image.getHeight());
    }

    /** Linearly sampled: the banner is drawn at fractional scales. */
    private static DynamicTexture linear(NativeImage image) {
        return new DynamicTexture(() -> "Lads kill banner", image) {
            { sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); }
        };
    }
}
