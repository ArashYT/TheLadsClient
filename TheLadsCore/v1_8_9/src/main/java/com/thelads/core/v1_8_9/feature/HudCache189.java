package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudFrameCap;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.render.GpuPhases189;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/**
 * The HUD FPS cap on 1.8.9 (1.7.3): the Lads HUD is drawn into an offscreen buffer at the cap rate (and at once after a resize),
 * and that buffer is blended over the screen as one quad on every frame. What animates by itself, the paper doll and armour items
 * (their enchantment glint), is not cached: it is drawn live every frame, in the poses and scissors it had, after the cached part.
 * The kill banner, crosshair, chat and the rest of the vanilla HUD were never capped. The buffer is Lads' own (HudBuffer189), so
 * OptiFine Fast Render and antialiasing keep the cache (1.9.12); only without driver framebuffer support does HudManager's capped
 * path draw its recorded HUD every frame, as before 1.7.3.
 * The buffer is cleared to transparent black and the HUD's straight-alpha blending is made to accumulate premultiplied alpha
 * (GlStateManagerMixin, OpenGlHelperMixin), so the one composite gives the colours drawing on the screen would have.
 */
public final class HudCache189 {
    private static final HudBuffer189 BUFFER = new HudBuffer189(false);
    /** What is drawn live every frame: the live draws and the poses and scissors around them. */
    private static final List<Consumer<LadsGraphics>> LIVE = new ArrayList<Consumer<LadsGraphics>>();
    /** True while the cache is drawn; read by the blend mixins. */
    private static boolean premultiply;
    private static boolean built, liveDraws;
    /** QA only (Probe172HudFlicker): the pre-1.7.3 path, for comparison. */
    static boolean qaOff;

    private HudCache189() {}

    /** Draws the Lads HUD through the cache; false when it is not used (cap off, no framebuffers) and HudManager draws it. */
    static boolean render(GuiLadsAdapter g) {
        Minecraft mc = Minecraft.getMinecraft();
        // Before HudFrameCap.due: without framebuffers HudManager's replay needs this frame's build.
        if (!HudFrameCap.enabled() || g.getGame() == null || !g.getGame().isIngame() || qaOff || !OpenGlHelper.framebufferSupported) {
            free();
            return false;
        }
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        long now = System.nanoTime();
        if (HudFrameCap.due(now, width, height) || !built) {
            BUFFER.newFrame(); // it can begin inside Autohide's capture
            if (!BUFFER.begin()) {
                free();
                return false;
            }
            built = liveDraws = false;
            LIVE.clear();
            premultiply = true;
            HudFrameCap.wholeHud = true; // HudManager draws, the cap is here
            HudFrameCap.exempt = Boolean.FALSE;
            blend();
            try {
                // Recorded and replayed into the cache: what it draws tells HudFrameCap whether the HUD animates (60 builds a second).
                HudManager.getInstance().renderMarked(new Split(mc.fontRendererObj, width, height), now);
                crop(HudManager.getInstance().markedBounds(), width, height);
                built = true;
            } finally {
                HudFrameCap.wholeHud = false;
                HudFrameCap.exempt = null;
                premultiply = false;
                blend();
                BUFFER.end();
            }
            if (!liveDraws) LIVE.clear(); // poses alone draw nothing
        }
        if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.LADS_HUD);
        BUFFER.draw(1, width, height, crop, crops);
        if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.LADS_HUD);
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

    /** The parts of the screen the cached HUD covers (x0, y0, x1, y1 each, GUI units): the composite blends only those (1.9.13; the
     *  whole window before). */
    private static double[] crop = new double[4 * 13];
    private static int crops;

    /** The build's boxes, a GUI unit wider all round and whole (still apart: RecordingGraphics keeps them over 4 apart), or the whole
     *  screen when the build had a draw of unknown size. */
    private static void crop(List<float[]> boxes, int width, int height) {
        if (boxes == null) {
            crop[0] = crop[1] = 0;
            crop[2] = width;
            crop[3] = height;
            crops = 1;
            return;
        }
        if (crop.length < boxes.size() * 4) crop = new double[boxes.size() * 4];
        crops = 0;
        for (float[] box : boxes) {
            int i = crops * 4;
            crop[i] = Math.max(0, Math.floor(box[0]) - 1);
            crop[i + 1] = Math.max(0, Math.floor(box[1]) - 1);
            crop[i + 2] = Math.min(width, Math.ceil(box[2]) + 1);
            crop[i + 3] = Math.min(height, Math.ceil(box[3]) + 1);
            if (crop[i + 2] > crop[i] && crop[i + 3] > crop[i + 1]) crops++;
        }
    }

    /** Whether the blend function src/dst gets premultiplied alpha factors (ONE, ONE_MINUS_SRC_ALPHA): only while the cache is drawn. */
    public static boolean premultiplied(int src, int dst) {
        return premultiply && src == GL11.GL_SRC_ALPHA && dst == GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    /** The GUI's blend function, sent to GL as the blend mixins see it now (GlStateManager skips a function it thinks is set). */
    private static void blend() {
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    /** Gives the window-size buffer back (cap off, out of a world). */
    public static void free() {
        BUFFER.free();
        built = false;
        LIVE.clear();
    }

    /** Draws into the cache, except the live draws, which it keeps (with the poses and scissors) for every frame. */
    private static final class Split extends GuiLadsAdapter {
        Split(FontRenderer font, int width, int height) {
            super(font, width, height);
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
    }
}
