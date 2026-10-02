package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 26.x KillBannerArt on 1.8.9: a skin banner frame (KillBannerPlayer) where Valorant puts it, the style's recoloured frame and
 * its overlays, as DynamicTextures (linearly sampled: the banner is drawn at fractional scales) on Tessellator quads.
 */
final class KillBannerArt189 {
    static final class Sprite {
        final DynamicTexture texture;
        final int width, height;
        Sprite(DynamicTexture texture, int width, int height) { this.texture = texture; this.width = width; this.height = height; }
    }
    private static final class Live {
        Sprite sprite;
        KillBannerStrip strip;
        int frame = -1, variant = -1;
        byte[] copy;
    }
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<KillBannerStyle, Live> LIVE = new EnumMap<>(KillBannerStyle.class);
    private static final Map<KillBannerStyle, int[]> BOUNDS = new EnumMap<>(KillBannerStyle.class);
    static final String BASE = "/assets/theladscore/textures/gui/base_kill_banner.png";
    private KillBannerArt189() {}

    static void draw(int guiWidth, int guiHeight, KillBannerStyle style, int variant, int kills, KillBannerStrip strip, KillBannerPlayer.Frame f, float size) {
        // Valorant's place and size: the ring centre at 79.4% of the screen height, cells at 1.15 px per 1080p pixel.
        float k = guiHeight * KillBannerStyle.SCREEN_SCALE / 1080f * size;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(guiWidth / 2f, guiHeight * .794f, 0.0F);
            GlStateManager.scale(k, k, 1.0F);
            GlStateManager.translate(-style.anchorX, -style.anchorY, 0.0F);
            if (f.shadowAlpha() > 0) {
                Sprite shadow = sprite("/assets/theladscore/killbanner/shadow.png");
                float d = style.ring * 3.7f;
                quad(shadow, style.anchorX - d / 2, style.anchorY - d / 2, d / shadow.width, argb(0, f.shadowAlpha()));
            }
            if (f.drawnExit()) {
                quad(exitLayer(style, kills, variant, strip, false), 0, 0, 1, argb(0xFFFFFF, f.restAlpha()));
                if (f.iconAlpha() > 0) iconLayer(style, exitLayer(style, kills, variant, strip, true), f.iconScale(), argb(0xFFFFFF, f.iconAlpha()));
            } else quad(frame(style, variant, strip, f.stripFrame()), 0, 0, 1, -1);
            if (f.heartAlpha() > 0) iconLayer(style, sprite(style.asset("heart.png")), f.iconScale(), argb(0xFFFFFF, f.heartAlpha()));
            if (f.strobe() > 0) iconLayer(style, sprite(style.asset("tint.png")), f.iconScale(), argb(KillBannerPlayer.STROBE_RED & 0xFFFFFF, f.strobe()));
            if (f.markSize() > 0) {
                float cx = style.anchorX, cy = style.anchorY + style.markY * f.iconScale();
                mark(sprite("/assets/theladscore/killbanner/mark_thin.png"), cx, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                mark(sprite("/assets/theladscore/killbanner/mark.png"), cx, cy, f.markSize(), f.markColor(), f.markAlpha());
            }
            if (f.labelAlpha() > 0) {
                Sprite label = sprite("/assets/theladscore/killbanner/headshot.png");
                float s = style.ring * 1.45f / label.width;
                quad(label, style.anchorX - label.width * s / 2, style.anchorY + style.labelY, s, argb(0xFFFFFF, f.labelAlpha()));
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Kill Banner picker art: the skin's settled one-kill frame in a variant, with its emblem and kill mark, cropped and fitted into the box. */
    static void thumb(KillBannerStyle style, int variant, int x, int y, int w, int h) {
        KillBannerStrip strip = style.strip(1);
        String key = style.id + "/thumb/" + variant;
        Sprite cell = SPRITES.get(key);
        if (cell == null) {
            byte[] rgba = strip.frame(strip.introEnd).clone();
            style.recolor(rgba, variant);
            SPRITES.put(key, cell = texture(rgba, strip.width, strip.height));
        }
        int[] box = BOUNDS.get(style);
        if (box == null) BOUNDS.put(style, box = bounds(strip.frame(strip.introEnd), strip.width, strip.height));
        float k = Math.min(w / (float) box[2], h / (float) box[3]);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x + (w - box[2] * k) / 2, y + (h - box[3] * k) / 2, 0.0F);
            GlStateManager.scale(k, k, 1.0F);
            GlStateManager.translate(-box[0], -box[1], 0.0F);
            quad(cell, 0, 0, 1, -1);
            if (style.heart) iconLayer(style, sprite(style.asset("heart.png")), 1, -1);
            mark(sprite("/assets/theladscore/killbanner/mark.png"), style.anchorX, style.anchorY + style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1);
        } finally {
            GlStateManager.popMatrix();
        }
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
        if (sprite != null) return sprite;
        try (InputStream in = KillBannerArt189.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException(path + " is missing");
            BufferedImage image = ImageIO.read(in);
            sprite = linear(new DynamicTexture(image), image.getWidth(), image.getHeight());
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner art unavailable: " + path, failure);
        }
        SPRITES.put(path, sprite);
        return sprite;
    }

    /** Reaver's settled icon or the rest of its banner, recoloured, for the drawn way out. */
    private static Sprite exitLayer(KillBannerStyle style, int kills, int variant, KillBannerStrip strip, boolean icon) {
        String key = style.id + "/k" + Math.min(5, kills) + "/" + variant + (icon ? "/icon" : "/rest");
        Sprite sprite = SPRITES.get(key);
        if (sprite != null) return sprite;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(icon ? strip.exitIcon : strip.exitRest));
            int w = image.getWidth(), h = image.getHeight();
            byte[] rgba = new byte[w * h * 4];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int c = image.getRGB(x, y), o = (y * w + x) * 4;
                rgba[o] = (byte) (c >> 16); rgba[o + 1] = (byte) (c >> 8); rgba[o + 2] = (byte) c; rgba[o + 3] = (byte) (c >>> 24);
            }
            style.recolor(rgba, variant);
            SPRITES.put(key, sprite = texture(rgba, w, h));
            return sprite;
        } catch (IOException failure) {
            throw new IllegalStateException("Kill banner way-out art unavailable: " + key, failure);
        }
    }

    /** The style's frame texture, rewritten when the frame, the strip or the variant changes. */
    private static Sprite frame(KillBannerStyle style, int variant, KillBannerStrip strip, int index) {
        Live live = LIVE.get(style);
        if (live == null) LIVE.put(style, live = new Live());
        if (live.sprite == null) { // one cell size a style
            live.copy = new byte[strip.width * strip.height * 4];
            live.sprite = new Sprite(new DynamicTexture(strip.width, strip.height), strip.width, strip.height);
            live.frame = -1;
        }
        if (live.strip != strip || live.frame != index || live.variant != variant) {
            System.arraycopy(strip.frame(index), 0, live.copy, 0, live.copy.length);
            style.recolor(live.copy, variant);
            write(live.sprite.texture, live.copy);
            live.strip = strip;
            live.frame = index;
            live.variant = variant;
        }
        return live.sprite;
    }

    private static Sprite texture(byte[] rgba, int w, int h) {
        DynamicTexture texture = new DynamicTexture(w, h);
        write(texture, rgba);
        return new Sprite(texture, w, h);
    }

    /** Straight RGBA bytes into the texture's ARGB pixels, uploaded linearly sampled. */
    private static void write(DynamicTexture texture, byte[] rgba) {
        int[] pixels = texture.getTextureData();
        for (int i = 0, o = 0; i < pixels.length; i++, o += 4)
            pixels[i] = (rgba[o + 3] & 255) << 24 | (rgba[o] & 255) << 16 | (rgba[o + 1] & 255) << 8 | rgba[o + 2] & 255;
        texture.updateDynamicTexture();
        linear(texture, 0, 0);
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
