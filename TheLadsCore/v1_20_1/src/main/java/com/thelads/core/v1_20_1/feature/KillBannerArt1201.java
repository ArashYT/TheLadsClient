package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.thelads.core.client.killbanner.KillBannerFx;
import com.thelads.core.client.killbanner.KillBannerPlayer;
import com.thelads.core.client.killbanner.KillBannerStrip;
import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KillBannerModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

final class KillBannerArt1201 {
    static final class Sprite {
        final ResourceLocation id;
        final int width, height;
        long used;
        Sprite(ResourceLocation id, int width, int height) { this.id = id; this.width = width; this.height = height; }
        ResourceLocation id() { return id; }
        int width() { return width; }
        int height() { return height; }
    }

    private static final class Live {
        DynamicTexture texture;
        ResourceLocation id;
        KillBannerStrip strip;
        int frame = -1, variant = -1;
        byte[] copy;
        long used;
    }

    private static final String DIR = "/assets/theladscore/killbanner/", SHADOW = DIR + "shadow.png",
        MARK = DIR + "mark.png", MARK_THIN = DIR + "mark_thin.png", HEADSHOT = DIR + "headshot.png", HS_MARK = DIR + "hs_mark.png",
        LABEL_BOX = DIR + "label_box.png";
    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<KillBannerStyle, Live> LIVE = new EnumMap<>(KillBannerStyle.class);
    private static final Map<KillBannerStyle, int[]> BOUNDS = new EnumMap<>(KillBannerStyle.class);
    private static final int[] PHASEGUARD_COLORS = { 0xED6D3B, 0x008BBD, 0x68BD42, 0xD6D642 };
    private static final long IDLE = 60_000_000_000L;
    private static long now, swept, warmed;

    private KillBannerArt1201() {}

    static void draw(GuiGraphics g, KillBannerStyle style, int variant, int kills, KillBannerStrip strip,
                     KillBannerPlayer.Frame f, float size, boolean mark, boolean label) {
        strip(g, style, variant, kills, strip, f, g.guiWidth() / 2f, g.guiHeight() * .794f, scale(g, size), mark, label);
    }

    static void draw(GuiGraphics g, KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l, float size, boolean mark, boolean label) {
        still(g, style, variant, kills, l, g.guiWidth() / 2f, g.guiHeight() * .794f, scale(g, size), mark, label);
    }

    private static boolean markOn() {
        return !(ModuleManager.getInstance().getModule("KillBanner") instanceof KillBannerModule module) || module.killMark.get();
    }

    private static boolean labelOn() {
        return !(ModuleManager.getInstance().getModule("KillBanner") instanceof KillBannerModule module) || module.headshotBanner.get();
    }

    private static float scale(GuiGraphics g, float size) {
        return g.guiHeight() * KillBannerStyle.SCREEN_SCALE / 1080f * size;
    }

    private static void strip(GuiGraphics g, KillBannerStyle style, int variant, int kills, KillBannerStrip strip,
                              KillBannerPlayer.Frame f, float x, float y, float k, boolean mark, boolean label) {
        now = System.nanoTime();
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(k, k, 1.0f);
            pose.translate(-style.anchorX, -style.anchorY, 0);
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
                ResourceLocation frame = frame(style, variant, strip, f.stripFrame());
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                g.blit(frame, 0, 0, 0, 0, strip.width, strip.height, strip.width, strip.height);
            }
            pose.translate(0, f.iconY(), 0);
            if (f.heartAlpha() > 0) iconLayer(g, style, sprite(style.asset("heart.png")), f.iconScale(), argb(0xFFFFFF, f.heartAlpha()));
            if (mark && f.strobe() > 0) iconLayer(g, style, sprite(style.asset("tint.png")), f.iconScale(),
                argb(KillBannerPlayer.STROBE_RED & 0xFFFFFF, f.strobe()));
            if (mark && f.markSize() > 0) {
                float cx = style.anchorX, cy = style.anchorY + style.markY * f.iconScale();
                mark(g, sprite(MARK_THIN), cx, cy, f.markSize(), f.markColor(), f.markThinAlpha());
                mark(g, sprite(MARK), cx, cy, f.markSize(), f.markColor(), f.markAlpha());
            }
            if (label && f.labelAlpha() > 0) label(g, style, variant, style.anchorX, style.anchorY + style.labelY, f.labelAlpha());
        } finally {
            pose.popPose();
        }
    }

    private static void still(GuiGraphics g, KillBannerStyle style, int variant, int kills, KillBannerPlayer.Layers l,
                              float x, float y, float k, boolean mark, boolean label) {
        now = System.nanoTime();
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(k * KillBannerStyle.ART_SCALE, k * KillBannerStyle.ART_SCALE, 1.0f);
            int accent = style.accent(variant), shade = grey(l.emblemShade());
            float r = style.ring, ey = l.emblemY(), es = l.emblemScale() * (mark ? l.emblemFlick() : 1);
            if (l.shadowAlpha() > 0) centred(g, sprite(SHADOW), 0, 0, r * 3.7f / sprite(SHADOW).width(), argb(0, l.shadowAlpha()));
            if (l.fx() >= 0) fx(g, style, variant, kills, l.fx(), accent);
            if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
                float s = 1.25f * es;
                if (l.tier() < 1) centred(g, sprite(style.swapAsset(kills - 1)), 0, ey, s, argb(shade, l.emblemAlpha() * (1 - l.tier())));
                centred(g, sprite(style.swapAsset(kills)), 0, ey, s, argb(shade, l.emblemAlpha() * l.tier()));
            } else {
                if (style.hasFrame) centred(g, sprite(style.frameAsset(variant)), 0, 0, l.frameScale(), argb(0xFFFFFF, l.frameAlpha()));
                if (style.hasRing) centred(g, sprite(style.ringAsset(variant)), 0, 0, l.ringScale(), argb(0xFFFFFF, l.ringAlpha()));
                if (style.hasEmblem) {
                    int base = style.type == KillBannerStyle.Type.PHASEGUARD ? PHASEGUARD_COLORS[Math.max(0, Math.min(3, variant))] : 0xFFFFFF;
                    int color = multiply(mix(base, KillBannerPlayer.STROBE_RED & 0xFFFFFF, mark ? l.strobe() : 0), shade);
                    String badge = l.headshot() ? style.headshotEmblemAsset(variant) : null;
                    centred(g, sprite(badge != null ? badge : style.emblemAsset(variant)), 0, ey, es, argb(color, l.emblemAlpha()));
                }
                if (style.hasPip && l.pipAlpha() > 0) {
                    Sprite hover = sprite(style.pipAsset(variant));
                    String upAsset = style.pipUpAsset(variant);
                    Sprite up = upAsset != null ? sprite(upAsset) : null;
                    int lit = mix(0xFFFFFF, accent, .25f);
                    float orbit = r * l.pipOrbit() * l.pipRadius();
                    float[] degrees = l.pipDegrees();
                    for (int i = 0; i < degrees.length; i++) {
                        float deg = degrees[i], rad = (float) Math.toRadians(deg);
                        float px = -orbit * (float) Math.sin(rad), py = -orbit * (float) Math.cos(rad);
                        pose.pushPose();
                        pose.translate(px, py, 0);
                        pose.mulPose(Axis.ZP.rotation(-rad));
                        if (up != null) centred(g, up, 0, 0, l.pipScale(), argb(0xFFFFFF, l.pipAlpha() * l.pipUp()));
                        if (l.pipFlare() > 0) centred(g, hover, 0, 0, l.pipScale(), argb(lit, l.pipAlpha() * l.pipFlare()));
                        pose.popPose();
                    }
                }
            }
            if (mark && l.markSize() > 0) {
                float cy = style.markY * es + ey;
                mark(g, sprite(MARK_THIN), 0, cy, l.markSize(), l.markColor(), l.markThinAlpha());
                mark(g, sprite(MARK), 0, cy, style.markSize * es, l.markColor(), l.markAlpha());
            }
            if (label && l.labelAlpha() > 0) {
                if (mark) {
                    Sprite hsMark = sprite(HS_MARK);
                    quad(g, hsMark, -hsMark.width() / 2f, style.markY * es + ey - hsMark.height() / 2f, 1f,
                        argb(KillBannerPlayer.MARK_RED & 0xFFFFFF, l.labelAlpha()));
                }
                label(g, style, variant, 0, style.labelY, l.labelAlpha());
            }
        } finally {
            pose.popPose();
        }
    }

    private static void fx(GuiGraphics g, KillBannerStyle style, int variant, int kills, float seconds, int accent) {
        if (kills >= 5) {
            for (int i = 0; i < 4; i++) {
                float sx = i == 0 || i == 3 ? -1 : 1, sy = i < 2 ? -1 : 1, angle = i == 0 ? -45 : i == 1 ? 45 : i == 2 ? 135 : -135;
                cell(g, KillBannerFx.X_SPARKS, seconds, sx * KillBannerFx.X_OFFSET, sy * KillBannerFx.X_OFFSET, KillBannerFx.X_W, KillBannerFx.X_H, angle, false, accent);
            }
        }
        cell(g, KillBannerFx.FLAME, seconds, 0, KillBannerFx.FLAME_Y, KillBannerFx.FLAME_W, KillBannerFx.FLAME_H, 0, false, accent);
        KillBannerFx.Book tier = KillBannerFx.tier(style, variant, kills);
        if (tier != null) {
            cell(g, tier, seconds, -KillBannerFx.TIER_SIZE / 2, 0, KillBannerFx.TIER_SIZE, KillBannerFx.TIER_SIZE, 0, true, accent);
            cell(g, tier, seconds, KillBannerFx.TIER_SIZE / 2, 0, KillBannerFx.TIER_SIZE, KillBannerFx.TIER_SIZE, 0, false, accent);
        }
        if (kills >= 5) cell(g, KillBannerFx.LARGE_SPARKS, seconds, 0, 0, KillBannerFx.LARGE_SIZE, KillBannerFx.LARGE_SIZE, 0, false, accent);
    }

    private static void cell(GuiGraphics g, KillBannerFx.Book book, float seconds, float cx, float cy, float w, float h, float angle, boolean flip, int rgb) {
        if (book == null) return;
        int c = book.cell(seconds);
        if (c < 0) return;
        Sprite atlas = sprite(book.asset());
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        if (angle != 0) pose.mulPose(Axis.ZP.rotation((float) Math.toRadians(angle)));
        pose.scale((flip ? -1 : 1) * w / book.cellW(), h / book.cellH(), 1.0f);
        pose.translate(-book.cellW() / 2f, -book.cellH() / 2f, 0);
        float r = ((rgb >> 16) & 255) / 255.0f;
        float gr = ((rgb >> 8) & 255) / 255.0f;
        float b = (rgb & 255) / 255.0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        g.blit(atlas.id(), 0, 0, (c % book.cols()) * book.cellW(), (c / book.cols()) * book.cellH(),
            book.cellW(), book.cellH(), atlas.width(), atlas.height());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        pose.popPose();
    }

    private static void label(GuiGraphics g, KillBannerStyle style, int variant, float cx, float top, float alpha) {
        Sprite box = sprite(LABEL_BOX), text = sprite(HEADSHOT);
        float w = style.ring * 1.45f, h = w * 27 / 105f;
        quad(g, box, cx - w / 2, top, w / box.width(), argb(0, .55f * alpha));
        quad(g, box, cx - w / 2, top, w / box.width(), argb(style.headshotBox(variant), KillBannerStyle.HEADSHOT_BOX_ALPHA * alpha));
        float s = h * .5f / text.height();
        quad(g, text, cx - text.width() * s / 2, top + (h - text.height() * s) / 2, s, argb(0xFFFFFF, alpha));
    }

    static void preview(GuiGraphics g, KillBannerStyle style, int variant, int x, int y, int w, int h, double clock) {
        int kills = KillBannerPlayer.previewKills(style, clock);
        double age = KillBannerPlayer.previewAge(style, clock), seconds = KillBannerPlayer.previewSeconds(style, kills);
        if (style.isAnimated()) {
            KillBannerStrip strip = style.strip(kills);
            KillBannerPlayer.Frame f = KillBannerPlayer.at(style, strip, age, seconds, false);
            if (f == null) return;
            float k = Math.min(w / (float) strip.width, h / (float) strip.height);
            strip(g, style, variant, kills, strip, f, x + w / 2f + (style.anchorX - strip.width / 2f) * k,
                y + h / 2f + (style.anchorY - strip.height / 2f) * k, k, markOn(), labelOn());
            return;
        }
        KillBannerPlayer.Layers l = KillBannerPlayer.layers(style, kills, age, seconds, true);
        if (l == null) return;
        float extent;
        if (style.type == KillBannerStyle.Type.BANNER_SWAP) extent = 1.25f * sprite(style.swapAsset(1)).width();
        else if (style.hasFrame) extent = Math.max(sprite(style.frameAsset(variant)).width(), sprite(style.frameAsset(variant)).height());
        else extent = style.ring * 2.6f;
        still(g, style, variant, kills, l, x + w / 2f, y + h / 2f, Math.min(w, h) * .9f / (extent * KillBannerStyle.ART_SCALE), markOn(), labelOn());
    }

    static void thumb(GuiGraphics g, KillBannerStyle style, int variant, int x, int y, int w, int h) {
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
            pose.pushPose();
            try {
                pose.translate(x + (w - box[2] * k) / 2, y + (h - box[3] * k) / 2, 0);
                pose.scale(k, k, 1.0f);
                pose.translate(-box[0], -box[1], 0);
                quad(g, cell, 0, 0, 1, -1);
                if (style.heart) iconLayer(g, style, sprite(style.asset("heart.png")), 1, -1);
                mark(g, sprite(MARK), style.anchorX, style.anchorY + style.markY, style.markSize, KillBannerPlayer.MARK_RED, 1);
            } finally {
                pose.popPose();
            }
            return;
        }

        if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
            Sprite swap = sprite(style.swapAsset(1));
            float sw = swap.width() * 1.25f, sh = swap.height() * 1.25f;
            float k = Math.min(w / sw, h / sh);
            var pose = g.pose();
            pose.pushPose();
            try {
                pose.translate(x + w / 2f, y + h / 2f, 0);
                pose.scale(k, k, 1.0f);
                quad(g, swap, -swap.width() * 1.25f / 2f, -swap.height() * 1.25f / 2f, 1.25f, -1);
            } finally {
                pose.popPose();
            }
            return;
        }

        Sprite frame = style.hasFrame ? sprite(style.frameAsset(variant)) : null;
        float bw = frame != null ? frame.width() : style.ring * 2.5f;
        float bh = frame != null ? frame.height() : style.ring * 2.5f;
        float k = Math.min(w / bw, h / bh);
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x + w / 2f, y + h / 2f, 0);
            pose.scale(k, k, 1.0f);
            if (frame != null) quad(g, frame, -frame.width() / 2f, -frame.height() / 2f, 1f, -1);
            if (style.hasRing) {
                Sprite ring = sprite(style.ringAsset(variant));
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
            pose.popPose();
        }
    }

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
        if (style.type == KillBannerStyle.Type.BANNER_SWAP) {
            for (int kills = 1; kills <= 5; kills++) sprite(style.swapAsset(kills));
            return;
        }
        if (style.hasFrame) sprite(style.frameAsset());
        if (style.hasRing) sprite(style.ringAsset());
        if (style.hasEmblem) sprite(style.emblemAsset(variant));
        if (style.hasPip) sprite(style.pipAsset(variant));
    }

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
        LIVE.entrySet().removeIf(e -> {
            if (time - e.getValue().used < IDLE) return false;
            textures.release(e.getValue().id);
            BOUNDS.remove(e.getKey());
            e.getKey().release();
            return true;
        });
    }

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

    private static void quad(GuiGraphics g, Sprite s, float x, float y, float scale, int color) {
        if ((color >>> 24) == 0) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0f);
        float a = ((color >>> 24) & 255) / 255.0f;
        float r = ((color >> 16) & 255) / 255.0f;
        float gr = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(s.id(), 0, 0, 0, 0, s.width(), s.height(), s.width(), s.height());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        pose.popPose();
    }

    private static void centred(GuiGraphics g, Sprite s, float x, float y, float scale, int color) {
        if ((color >>> 24) == 0) return;
        quad(g, s, x - s.width() * scale / 2, y - s.height() * scale / 2, scale, color);
    }

    private static void iconLayer(GuiGraphics g, KillBannerStyle style, Sprite s, float scale, int color) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(style.anchorX, style.anchorY, 0);
        pose.scale(scale, scale, 1.0f);
        pose.translate(-style.anchorX, -style.anchorY, 0);
        float a = ((color >>> 24) & 255) / 255.0f;
        float r = ((color >> 16) & 255) / 255.0f;
        float gr = ((color >> 8) & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(s.id(), 0, 0, 0, 0, s.width(), s.height(), s.width(), s.height());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        pose.popPose();
    }

    private static void mark(GuiGraphics g, Sprite s, float cx, float cy, float size, int color, float alpha) {
        if (alpha <= 0) return;
        quad(g, s, cx - size / 2, cy - size / 2, size / s.width(), argb(color, alpha));
    }

    private static Sprite cached(String key, Function<String, Sprite> make) {
        Sprite sprite = SPRITES.computeIfAbsent(key, make);
        sprite.used = now;
        return sprite;
    }

    private static Sprite sprite(String path) {
        return cached(path, p -> {
            try (InputStream in = KillBannerArt1201.class.getResourceAsStream(p)) {
                if (in == null) throw new IOException(p + " is missing");
                String name = p.substring(p.indexOf("/killbanner/") + 12).replace(".png", "").replace('/', '_');
                return register("killbanner/" + name, NativeImage.read(in));
            } catch (IOException failure) {
                throw new IllegalStateException("Kill banner art unavailable: " + p, failure);
            }
        });
    }

    private static Sprite exitLayer(KillBannerStyle style, int kills, int variant, KillBannerStrip strip, boolean icon) {
        String key = style.id + "/k" + Math.min(5, kills) + "/" + variant + (icon ? "/icon" : "/rest");
        return cached(key, k -> {
            try {
                NativeImage image = NativeImage.read(icon ? strip.exitIcon : strip.exitRest);
                int w = image.getWidth(), h = image.getHeight();
                byte[] rgba = new byte[w * h * 4];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = image.getPixelRGBA(x, y), o = (y * w + x) * 4;
                    rgba[o] = (byte) (c & 255); rgba[o + 1] = (byte) ((c >> 8) & 255); rgba[o + 2] = (byte) ((c >> 16) & 255); rgba[o + 3] = (byte) (c >>> 24);
                }
                style.recolor(rgba, variant);
                write(image, rgba, w, h);
                return register("killbanner/exit/" + key.replace('/', '_'), image);
            } catch (IOException failure) {
                throw new IllegalStateException("Kill banner way-out art unavailable: " + key, failure);
            }
        });
    }

    private static ResourceLocation frame(KillBannerStyle style, int variant, KillBannerStrip strip, int index) {
        Live live = LIVE.computeIfAbsent(style, s -> new Live());
        live.used = now;
        if (live.texture == null) {
            live.copy = new byte[strip.width * strip.height * 4];
            NativeImage image = new NativeImage(strip.width, strip.height, true);
            live.id = new ResourceLocation("theladscore", "killbanner/frame_" + style.id);
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
            image.setPixelRGBA(x, y, (rgba[o + 3] & 255) << 24 | (rgba[o + 2] & 255) << 16 | (rgba[o + 1] & 255) << 8 | (rgba[o] & 255));
        }
    }

    private static Sprite register(String path, NativeImage image) {
        ResourceLocation id = new ResourceLocation("theladscore", path);
        Minecraft.getInstance().getTextureManager().register(id, linear(image));
        return new Sprite(id, image.getWidth(), image.getHeight());
    }

    private static DynamicTexture linear(NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        return texture;
    }
}
