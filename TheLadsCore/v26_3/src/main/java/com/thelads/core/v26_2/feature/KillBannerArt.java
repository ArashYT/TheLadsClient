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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Draws a skin banner frame (KillBannerPlayer) where Valorant puts it: the style's recoloured frame and its overlays. */
final class KillBannerArt {
    private record Sprite(Identifier id, int width, int height) {}
    private static final class Live {
        DynamicTexture texture;
        Identifier id;
        KillBannerStrip strip;
        int frame = -1, variant = -1;
        byte[] copy;
    }
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<KillBannerStyle, Live> LIVE = new EnumMap<>(KillBannerStyle.class);
    private static final Map<KillBannerStyle, int[]> BOUNDS = new EnumMap<>(KillBannerStyle.class);
    private static final int[] PHASEGUARD_COLORS = { 0xED6D3B, 0x008BBD, 0x68BD42, 0xD6D642 };

    private KillBannerArt() {}

    static void draw(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int kills, KillBannerStrip strip,
                     KillBannerPlayer.Frame f, float size) {
        // Valorant's place and size: the ring centre at 79.4% of the screen height, cells at 1.15 px per 1080p pixel.
        float k = g.guiHeight() * KillBannerStyle.SCREEN_SCALE / 1080f * size;
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(g.guiWidth() / 2f, g.guiHeight() * .794f);
            pose.scale(k, k);

            if (style.isAnimated() && strip != null) {
                pose.translate(-style.anchorX, -style.anchorY);
                if (f.shadowAlpha() > 0) {
                    Sprite shadow = sprite("/assets/theladscore/killbanner/shadow.png");
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
                    mark(g, sprite("/assets/theladscore/killbanner/mark_thin.png"), cx, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                    mark(g, sprite("/assets/theladscore/killbanner/mark.png"), cx, cy, f.markSize(), f.markColor(), f.markAlpha());
                }
                if (f.labelAlpha() > 0) {
                    Sprite label = sprite("/assets/theladscore/killbanner/headshot.png");
                    float s = style.ring * 1.45f / label.width();
                    quad(g, label, style.anchorX - label.width() * s / 2, style.anchorY + style.labelY, s, argb(0xFFFFFF, f.labelAlpha()));
                }
            } else {
                // Composite, Swap, Phaseguard kill banners: Kingdom Archives art, in its own pixels
                pose.scale(KillBannerStyle.ART_SCALE, KillBannerStyle.ART_SCALE);
                if (f.shadowAlpha() > 0) {
                    Sprite shadow = sprite("/assets/theladscore/killbanner/shadow.png");
                    float d = style.ring * 3.7f;
                    quad(g, shadow, -d / 2f, -d / 2f, d / shadow.width(), argb(0, f.shadowAlpha()));
                }

                if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                    Sprite swap = sprite(style.swapAsset(kills));
                    float swapScale = 1.25f * f.iconScale();
                    float sw = swap.width() * swapScale, sh = swap.height() * swapScale;
                    quad(g, swap, -sw / 2f, -sh / 2f, swapScale, argb(0xFFFFFF, f.restAlpha()));
                } else {
                    if (style.hasFrame) {
                        Sprite frame = sprite(style.frameAsset());
                        quad(g, frame, -frame.width() / 2f, -frame.height() / 2f, 1f, argb(0xFFFFFF, f.restAlpha()));
                    }
                    if (style.hasRing) {
                        Sprite ring = sprite(style.ringAsset());
                        quad(g, ring, -ring.width() / 2f, -ring.height() / 2f, 1f, argb(0xFFFFFF, f.restAlpha()));
                    }
                    if (style.hasEmblem) {
                        Sprite emblem = sprite(style.emblemAsset(variant));
                        int baseColor = style.type == KillBannerStyle.Type.PHASEGUARD
                            ? PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))]
                            : 0xFFFFFF;
                        int color = f.strobe() > 0 ? mix(baseColor, KillBannerPlayer.STROBE_RED & 0xFFFFFF, f.strobe()) : baseColor;
                        float es = f.iconScale();
                        float ew = emblem.width() * es, eh = emblem.height() * es;
                        quad(g, emblem, -ew / 2f, -eh / 2f, es, argb(color, f.iconAlpha()));
                    }
                    if (style.hasPip) {
                        Sprite pip = sprite(style.pipAsset(variant));
                        int ct = Math.max(1, Math.min(6, kills));
                        float step = 360f / ct;
                        float r = style.ring;
                        for (int s = 0; s < ct; s++) {
                            float deg = step * (s + 1);
                            if (ct == 2) deg += 90f;
                            float rad = (float) Math.toRadians(deg);
                            float px = -r * (float) Math.sin(rad);
                            float py = -r * (float) Math.cos(rad);
                            pose.pushMatrix();
                            pose.translate(px, py);
                            pose.rotate(-rad);
                            quad(g, pip, -pip.width() / 2f, -pip.height() / 2f, 1f, argb(0xFFFFFF, f.restAlpha()));
                            pose.popMatrix();
                        }
                    }
                }

                if (f.markSize() > 0) {
                    float cy = style.markY * f.iconScale();
                    mark(g, sprite("/assets/theladscore/killbanner/mark_thin.png"), 0, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                    mark(g, sprite("/assets/theladscore/killbanner/mark.png"), 0, cy, f.markSize(), f.markColor(), f.markAlpha());
                }
                if (f.labelAlpha() > 0) {
                    Sprite hsMark = sprite("/assets/theladscore/killbanner/hs_mark.png");
                    float cy = style.markY * f.iconScale();
                    quad(g, hsMark, -hsMark.width() / 2f, cy - hsMark.height() / 2f, 1f, argb(KillBannerPlayer.MARK_RED & 0xFFFFFF, f.labelAlpha()));

                    Sprite label = sprite("/assets/theladscore/killbanner/headshot.png");
                    float s = style.ring * 1.45f / label.width();
                    quad(g, label, -label.width() * s / 2f, style.labelY, s, argb(0xFFFFFF, f.labelAlpha()));
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    /** Kill Banner picker art: the skin's settled one-kill frame in a variant, with its emblem and kill mark, cropped and fitted into the box. */
    static void thumb(GuiGraphicsExtractor g, KillBannerStyle style, int variant, int x, int y, int w, int h) {
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(1);
            Sprite cell = SPRITES.computeIfAbsent(style.id + "/thumb/" + variant, key -> {
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
                mark(g, sprite("/assets/theladscore/killbanner/mark.png"), style.anchorX, style.anchorY + style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1);
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
            mark(g, sprite("/assets/theladscore/killbanner/mark.png"), 0, style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1f);
        } finally {
            pose.popMatrix();
        }
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

    private static int mix(int a, int b, float t) {
        float inv = 1 - t;
        int r = Math.round(((a >> 16) & 255) * inv + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * inv + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * inv + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static void quad(GuiGraphicsExtractor g, Sprite s, float x, float y, float scale, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.blit(RenderPipelines.GUI_TEXTURED, s.id(), 0, 0, 0, 0, s.width(), s.height(), s.width(), s.height(), color);
        pose.popMatrix();
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

    private static Sprite sprite(String path) {
        return SPRITES.computeIfAbsent(path, p -> {
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
        return SPRITES.computeIfAbsent(key, k -> {
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
