package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/**
 * The HUD editor's game view. The GUI is extracted before the frame renders, when the main target still holds the last frame
 * (with the editor in it), so the editor only queues a blit of its own texture here; RenderScaleMixin fills that texture with this
 * frame's world after the world pass and before the GUI draws. The vanilla HUD is then extracted again, scaled into the box.
 */
public final class HudEditorView {
    private static RenderTarget copy;
    private static boolean wanted;
    private HudEditorView() {}

    public static boolean draw(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        var mc = Minecraft.getInstance();
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (mc.level == null || !NativeRenderScale.ready(main)) return false;
        if (!NativeRenderScale.ready(copy) || copy.width != main.width || copy.height != main.height) {
            release();
            copy = new TextureTarget("Lads HUD editor view", main.width, main.height, false, main.getColorTexture().getFormat());
        }
        wanted = true;
        // Opaque: the world target's alpha is not coverage. Render targets are bottom-up, hence v from 1 to 0.
        mc.gameRenderer.gameRenderState().guiRenderState.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
            TextureSetup.singleTexture(copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(g.pose()), x, y, x + width, y + height, 0, 1, 1, 0, -1, null));
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            g.pose().scale(width / (float) g.guiWidth(), height / (float) g.guiHeight());
            mc.gui.hud.extractRenderState(g, mc.getDeltaTracker()); // the Lads HUD skips itself while the editor is open
        } finally {
            g.pose().popMatrix();
        }
        return true;
    }

    /** Render thread, after the world pass and before the GUI. */
    public static void copyWorld(RenderTarget main) {
        if (!wanted) return;
        wanted = false;
        if (NativeRenderScale.ready(copy) && NativeRenderScale.ready(main)) NativeRenderScale.blit(main, copy, false);
    }

    public static void release() {
        wanted = false;
        if (copy == null) return;
        copy.destroyBuffers();
        copy = null;
    }
}
