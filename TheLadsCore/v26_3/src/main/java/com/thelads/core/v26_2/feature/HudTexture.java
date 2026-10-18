package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

/**
 * 1.9.12 perf round 2: the capped HUD (HudCapture) kept as one texture between builds. A replayed HUD still went through the whole
 * GUI pipeline every frame (every glyph, item and blit meshed, sorted and drawn again: ~25 % of the render thread at 1,300 FPS);
 * now a build is extracted into a GUI state of its own, drawn by a second GuiRenderer into a window-size target at the start of the
 * frame's GUI pass, and every frame shows that target as one premultiplied blit where the HUD was. Rendered into a transparent
 * target with the GUI's normal blending, the result is premultiplied, so the blit composites exactly as the elements did.
 * <p>
 * Elements whose blending reads what is under them (the crosshair's inversion, the vignette, nausea's additive overlay) cannot be
 * baked: they go to the frame's GUI as they are, under the HUD image, every frame. Picture renderers (the paper doll, Xaero's
 * minimap) are the main GUI's own, shared; Xaero's in-world waypoint pass at the start of a GUI render runs for the main GUI only.
 * -Dlads.hudTexture=false = the replay path.
 */
public final class HudTexture {
    /** What a HUD renderer needs from the main one (GuiRendererHudTextureMixin). */
    public interface Renderer {
        FeatureRenderDispatcher lads$features();
        Map<Class<?>, Object> lads$pictureRenderers();
        void lads$sharePictureRenderers(Map<Class<?>, Object> renderers);
    }

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("lads.hudTexture", "true"));
    private static final Vector4f CLEAR = new Vector4f(0);
    private static final GuiRenderState STATE = new GuiRenderState();
    /** Elements of the last build that are drawn as they are every frame, in their order. */
    private static final List<GuiElementRenderState> LIVE = new ArrayList<>();
    private static GuiRenderer renderer;
    private static TextureTarget target;
    private static BlitRenderState blit;
    /** STATE holds a build that is not drawn into the target yet; the build that is running adds into STATE; the last build drew
     *  nothing (F1): no blit. */
    private static boolean dirty, building, empty;
    /** The HUD renderer is drawing: Xaero's in-world pass skips it (XaeroMinimapHudPassMixin). */
    public static boolean inHudPass;
    /** QA: frames shown from the texture, builds drawn into it. */
    public static int qaShown, qaDrawn;

    private HudTexture() {}

    /** The texture path runs once the HUD renderer exists (made at the first GUI pass) and the window size is known. */
    public static boolean active() {
        return ENABLED && renderer != null;
    }

    /** Active, and the window has a size (not minimized). */
    public static boolean usable() {
        var window = Minecraft.getInstance().getWindow();
        return active() && window.getWidth() > 0 && window.getHeight() > 0;
    }

    /** The target no longer matches the window: the next frame must build (HudCapture resets the cap). */
    public static boolean sizeChanged() {
        var window = Minecraft.getInstance().getWindow();
        return active() && (target == null || target.width != window.getWidth() || target.height != window.getHeight());
    }

    /** A capped build: extracted into the HUD's own state instead of the frame's. */
    public static void build(Consumer<GuiGraphicsExtractor> build) {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int w = window.getWidth(), h = window.getHeight();
        if (target == null) target = new TextureTarget("Lads HUD", w, h, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        else if (target.width != w || target.height != h) target.resize(w, h);
        float scale = window.getGuiScale();
        blit = new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            TextureSetup.singleTexture(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
            new Matrix3x2f().scaling(w / scale, h / scale), 0, 0, 1, 1, 0, 1, 1, 0, -1, null);
        STATE.reset();
        LIVE.clear();
        dirty = false;
        building = true;
        try {
            build.accept(new GuiGraphicsExtractor(mc, STATE, (int) mc.mouseHandler.getScaledXPos(window), (int) mc.mouseHandler.getScaledYPos(window)));
            dirty = true;
        } catch (Throwable t) {
            blit = null; // never show a partial build
            throw t;
        } finally {
            building = false;
        }
    }

    /** GuiRenderStateCaptureMixin: an element of the build that has to be drawn live (true: it is kept, not added to the HUD state). */
    public static boolean keepLive(GuiRenderState state, GuiElementRenderState element) {
        if (!building || state != STATE) return false;
        var blend = element.pipeline().getColorTargetStates().getFirst().blendFunction().orElse(null);
        if (BlendFunction.TRANSLUCENT.equals(blend) || BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA.equals(blend)) return false;
        LIVE.add(element);
        return true;
    }

    /** HudCapture, after a build: whether it recorded anything to draw. */
    public static void built(boolean drew) {
        empty = !drew && LIVE.isEmpty();
    }

    /** Every frame: the live elements, then the HUD image above them (false: no image yet, replay instead). */
    public static boolean show(GuiRenderState state) {
        if (blit == null) return false;
        if (empty) return true;
        for (var element : LIVE) state.addGuiElement(element);
        state.addGuiElement(blit);
        qaShown++;
        return true;
    }

    /** GuiRendererHudTextureMixin, at the start of the frame's GUI render: draws a waiting build into the target. */
    public static void beforeGui(GuiRenderer main) {
        if (main == renderer) return;
        if (renderer == null) {
            if (!ENABLED) return;
            var mainRenderer = (Renderer) main;
            renderer = new GuiRenderer(STATE, mainRenderer.lads$features(), List.of());
            ((Renderer) renderer).lads$sharePictureRenderers(mainRenderer.lads$pictureRenderers());
            return; // the next capped build uses it
        }
        if (!dirty || target == null) return;
        dirty = false;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), CLEAR, target.getDepthTexture(), 0.0);
        inHudPass = true;
        try {
            renderer.render();
            renderer.endFrame();
            qaDrawn++;
        } finally {
            inHudPass = false;
        }
    }

    /** GuiRendererHudTextureMixin: where a GUI renderer draws (the HUD renderer: the target). */
    public static boolean isHudRenderer(GuiRenderer guiRenderer) {
        return guiRenderer == renderer && renderer != null;
    }

    public static TextureTarget target() {
        return target;
    }
}
