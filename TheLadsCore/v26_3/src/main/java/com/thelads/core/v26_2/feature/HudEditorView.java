package com.thelads.core.v26_2.feature;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

/**
 * The HUD editor's game view. The GUI is extracted before the frame renders, when the main target still holds the last frame
 * (with the editor in it), so the editor only queues a blit of its own texture here; RenderScaleMixin fills that texture with this
 * frame's world after the world pass and before the GUI draws. The vanilla HUD is then extracted again, scaled into the box.
 */
public final class HudEditorView {
    /**
     * Vanilla's fullscreen copy, writing colour only into a pass cleared to opaque black: the world target's sky has alpha 0,
     * which every GUI texture shader discards.
     */
    private static final RenderPipeline COPY = RenderPipeline.builder().withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withLocation(Identifier.fromNamespaceAndPath("thelads", "pipeline/hud_editor_view"))
        .withVertexShader("core/screenquad").withFragmentShader("core/blit_screen").withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
        .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
    private static RenderTarget copy;
    private static boolean wanted;
    private HudEditorView() {}

    public static boolean draw(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        return draw(g, x, y, width, height, true);
    }

    /** {@code hud}: also the vanilla HUD, as the HUD editor shows it; without it, the world alone (Fullbright's preview). */
    public static boolean draw(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean hud) {
        var mc = Minecraft.getInstance();
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (mc.level == null || !NativeRenderScale.ready(main)) return false;
        if (!NativeRenderScale.ready(copy) || copy.width != main.width || copy.height != main.height) {
            release();
            copy = new TextureTarget("Lads HUD editor view", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
        }
        wanted = true;
        // Render targets are bottom-up, hence v from 1 to 0. Added as a GUI element, so it sorts above the preview backdrop.
        mc.gameRenderer.gameRenderState().guiRenderState.addGuiElement(new BlitRenderState(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
            TextureSetup.singleTexture(copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
            new Matrix3x2f(g.pose()), x, y, x + width, y + height, 0, 1, 1, 0, -1, null));
        if (!hud) return true;
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
        if (!NativeRenderScale.ready(copy) || !NativeRenderScale.ready(main)) return;
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Lads HUD editor view",
                copy.getColorTextureView(), Optional.of(new Vector4f(0, 0, 0, 1)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(COPY));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", main.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    public static void release() {
        wanted = false;
        if (copy == null) return;
        copy.destroyBuffers();
        copy = null;
    }
}
