package com.thelads.core.v1_20_1.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudFrameCap;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.v1_20_1.game.mixin.MinecraftTargetAccessor;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * 1.9.12 perf: the HUD FPS cap on 1.20.1 as on 1.8.9 (HudCache189). The Lads HUD under the cap is drawn into a window-size offscreen
 * target at the cap rate (at once after a resize, and 60 times a second while it animates) and that target is blended over the
 * screen as one quad on every frame. Before, the recorded HUD (every glyph, fill and blit) was drawn again on every frame: about
 * 0.9 ms a frame on the Legion bench. What animates by itself, the paper doll and armour items (their enchantment glint), is drawn
 * live every frame in the poses and scissors it had, after the cached part; so are the HUDs set to ignore the cap.
 * The target is cleared to transparent black and the HUD's straight-alpha blending is made to accumulate premultiplied alpha
 * (GlStateManagerHudMixin), so the one composite gives the colours drawing on the screen would have.
 */
public final class HudCache1201 {
    private static TextureTarget target;
    /** What is drawn live every frame: the live draws and the poses and scissors around them. */
    private static final List<Consumer<LadsGraphics>> LIVE = new ArrayList<>();
    /** True while the cache is drawn; read by the blend mixin. */
    private static boolean premultiply;
    private static boolean built, liveDraws;
    /** QA / A-B: -Dlads.hudCache=false draws the recorded HUD every frame, as before 1.9.12. */
    public static volatile boolean off = !Boolean.parseBoolean(System.getProperty("lads.hudCache", "true"));

    private HudCache1201() {}

    /** Draws the Lads HUD through the cache; false when it is not used (cap off, out of a world) and HudManager draws it. */
    public static boolean render(GuiGraphics graphics, GuiGraphicsLadsAdapter1201 g) {
        if (mapMain != null) restoreMap(); // Xaero's draw did not return: never leave its cache as the main target
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (off || !HudFrameCap.enabled() || g.getGame() == null || !g.getGame().isIngame() || main.width <= 0 || main.height <= 0) {
            free();
            return false;
        }
        if (target != null && (target.width != main.width || target.height != main.height)) built = false;
        long now = System.nanoTime();
        if (HudFrameCap.due(now, g.getScaledWidth(), g.getScaledHeight()) || !built) build(graphics, main, now);
        if (!built) return true; // a failed build: nothing this frame, the next one builds again
        composite(graphics, main, target);
        for (Consumer<LadsGraphics> op : LIVE) op.accept(g);
        HudFrameCap.wholeHud = true;
        HudFrameCap.exempt = Boolean.TRUE;
        try {
            HudManager.getInstance().render(g);
        } finally {
            HudFrameCap.wholeHud = false;
            HudFrameCap.exempt = null;
        }
        return true;
    }

    private static void build(GuiGraphics graphics, RenderTarget main, long now) {
        graphics.flush(); // what is batched so far belongs on the screen
        target = ready(target, main);
        built = liveDraws = false;
        LIVE.clear();
        premultiply = true;
        blend();
        HudFrameCap.wholeHud = true; // HudManager draws, the cap is here
        HudFrameCap.exempt = Boolean.FALSE;
        try {
            // Recorded and replayed into the cache: what it draws tells HudFrameCap whether the HUD animates.
            HudManager.getInstance().renderMarked(new Split(graphics), now);
            graphics.flush();
            built = true;
        } finally {
            HudFrameCap.wholeHud = false;
            HudFrameCap.exempt = null;
            premultiply = false;
            blend();
            main.bindWrite(true);
            if (!built) HudFrameCap.reset();
        }
        if (!liveDraws) LIVE.clear(); // poses alone draw nothing
    }

    /** The cache over the GUI area as one quad (premultiplied: ONE, ONE_MINUS_SRC_ALPHA). */
    private static void composite(GuiGraphics graphics, RenderTarget main, TextureTarget target) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        float w = (float) (main.width / scale), h = (float) (main.height / scale);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder quad = Tesselator.getInstance().getBuilder();
        quad.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        quad.vertex(pose, 0, h, 0).uv(0, 0).endVertex();
        quad.vertex(pose, w, h, 0).uv(1, 0).endVertex();
        quad.vertex(pose, w, 0, 0).uv(1, 1).endVertex();
        quad.vertex(pose, 0, 0, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(quad.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    /** A window-size target, cleared to transparent black (the composite is premultiplied) and bound for drawing. */
    private static TextureTarget ready(TextureTarget t, RenderTarget main) {
        if (t == null) {
            t = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            t.setClearColor(0, 0, 0, 0);
        } else if (t.width != main.width || t.height != main.height) {
            t.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        t.clear(Minecraft.ON_OSX);
        t.bindWrite(true);
        return t;
    }

    private static TextureTarget map;
    private static RenderTarget mapMain;
    private static boolean mapBuilt;
    private static long mapLast;

    /**
     * Xaero's minimap HUD (XaeroHudMixin) under the same cap: it re-rendered its map into its own buffer and drew it on every frame
     * (spawn view 703 FPS with it, 1162 without, Legion). It is now drawn into a second cache at the HUD cap rate and that is
     * composited where it drew, as 26.3's whole-HUD capture already does. True when the cached image was drawn instead (cancel).
     */
    public static boolean mapBegin(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (off || mapMain != null || !HudFrameCap.enabled() || mc.level == null || main.width <= 0 || main.height <= 0) {
            if (mapMain == null) freeMap();
            return false;
        }
        long now = System.nanoTime(), interval = 1_000_000_000L / Math.max(1, HudSettings.getInstance().getHudFpsLimit());
        if (mapBuilt && map.width == main.width && map.height == main.height && now - mapLast < interval) {
            composite(graphics, main, map);
            return true;
        }
        mapLast = mapBuilt && now - mapLast < 2 * interval ? mapLast + interval : now; // on schedule, as HudFrameCap
        graphics.flush();
        map = ready(map, main);
        mapBuilt = false;
        mapMain = main;
        ((MinecraftTargetAccessor) mc).ladsSetMainTarget(map); // Xaero binds "the main target" again after its own map buffer
        premultiply = true;
        blend();
        return false;
    }

    /** After Xaero drew into the cache: back to the screen, and the new image composited. */
    public static void mapEnd(GuiGraphics graphics) {
        RenderTarget main = mapMain;
        if (main == null) return;
        graphics.flush();
        restoreMap();
        mapBuilt = true;
        composite(graphics, main, map);
    }

    /** The minimap is hidden (tab list, F3): built again when it shows. */
    public static void mapHidden() {
        mapBuilt = false;
    }

    private static void restoreMap() {
        premultiply = false;
        blend();
        ((MinecraftTargetAccessor) Minecraft.getInstance()).ladsSetMainTarget(mapMain);
        mapMain.bindWrite(true);
        mapMain = null;
    }

    private static void freeMap() {
        mapBuilt = false;
        if (map == null) return;
        map.destroyBuffers();
        map = null;
    }

    /** Whether the blend function src/dst gets premultiplied alpha factors (ONE, ONE_MINUS_SRC_ALPHA): only while the cache is drawn. */
    public static boolean premultiplied(int src, int dst) {
        return premultiply && src == GL11.GL_SRC_ALPHA && dst == GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    /** The GUI's blend function, sent to GL as the blend mixin sees it now (GlStateManager skips a function it thinks is set). */
    private static void blend() {
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        RenderSystem.defaultBlendFunc();
    }

    /** Gives the window-size target back (cap off, out of a world); made again on the next build. */
    public static void free() {
        built = false;
        LIVE.clear();
        if (target == null) return;
        target.destroyBuffers();
        target = null;
    }

    /** Draws into the cache, except the live draws, which it keeps (with the poses and scissors) for every frame. */
    private static final class Split extends GuiGraphicsLadsAdapter1201 {
        Split(GuiGraphics graphics) {
            super(graphics);
        }

        @Override public void pushPose() { super.pushPose(); LIVE.add(LadsGraphics::pushPose); }
        @Override public void popPose() { super.popPose(); LIVE.add(LadsGraphics::popPose); }
        @Override public void translate(float x, float y) { super.translate(x, y); LIVE.add(g -> g.translate(x, y)); }
        @Override public void scale(float sx, float sy) { super.scale(sx, sy); LIVE.add(g -> g.scale(sx, sy)); }
        @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {
            super.enableScissor(minX, minY, maxX, maxY);
            LIVE.add(g -> g.enableScissor(minX, minY, maxX, maxY));
        }
        @Override public void disableScissor() { super.disableScissor(); LIVE.add(LadsGraphics::disableScissor); }

        @Override public void drawPlayerModel(int x, int y, int width, int height, boolean editor) {
            liveDraws = true;
            LIVE.add(g -> g.drawPlayerModel(x, y, width, height, editor));
        }
        @Override public void drawArmorItem(int index, int x, int y, boolean preview) {
            liveDraws = true;
            LIVE.add(g -> g.drawArmorItem(index, x, y, preview));
        }
        @Override public void drawArmorSlot(int slot, int x, int y, boolean preview) {
            liveDraws = true;
            LIVE.add(g -> g.drawArmorSlot(slot, x, y, preview));
        }
        @Override public void drawInventorySlot(int slot, int x, int y, boolean preview) {
            liveDraws = true;
            LIVE.add(g -> g.drawInventorySlot(slot, x, y, preview));
        }
    }
}
