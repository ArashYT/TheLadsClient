package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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
            pose.translate(-style.anchorX, -style.anchorY);
            if (f.shadowAlpha() > 0) {
                Sprite shadow = sprite("/assets/theladscore/killbanner/shadow.png");
                float d = style.ring * 3.7f;
                quad(g, shadow, style.anchorX - d / 2, style.anchorY - d / 2, d / shadow.width(), argb(0, f.shadowAlpha()));
            }
            if (f.drawnExit()) {
                quad(g, exitLayer(style, kills, variant, strip, false), 0, 0, 1, argb(0xFFFFFF, f.restAlpha()));
                if (f.iconAlpha() > 0)
                    iconLayer(g, style, exitLayer(style, kills, variant, strip, true), f.iconScale(), argb(0xFFFFFF, f.iconAlpha()));
            } else {
                Identifier frame = frame(style, variant, strip, f.stripFrame());
                g.blit(RenderPipelines.GUI_TEXTURED, frame, 0, 0, 0, 0, strip.width, strip.height, strip.width, strip.height, -1);
            }
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
        } finally {
            pose.popMatrix();
        }
    }

    private static int argb(int rgb, float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | rgb & 0xFFFFFF;
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
                return register("killbanner/" + p.substring(p.indexOf("/killbanner/") + 12).replace(".png", ""), NativeImage.read(in));
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
