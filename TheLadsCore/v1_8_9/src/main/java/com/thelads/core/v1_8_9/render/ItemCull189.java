package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.FaceCull;
import com.thelads.core.v1_8_9.feature.GlState189;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Lads Flerovium, items, on 1.8.9 (1.8.5): RenderItem.renderModel's quad lists leave out the quads the GPU would cull anyway (back
 * faces, a flat item's edge strips seen square-on in the GUI) and far dropped or framed items' sub-pixel quads (LOD). 1.8.9 keeps
 * its matrices in the GL fixed-function stacks, so each model reads the current model-view and projection once; nothing is culled
 * while GL face culling is off, so the picture is the same either way. Works with and without OptiFine (both draw the lists).
 */
public final class ItemCull189 {
    private static final float LOD_DISTANCE = 32, LOD_PIXELS = 0.25f;
    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16), PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final float[] MV = new float[16], P = new float[16], MATRIX = new float[16];
    private static final List<BakedQuad> KEPT = new ArrayList<BakedQuad>();
    private static ItemCameraTransforms.TransformType context;
    private static boolean active;
    private static float lod;

    private ItemCull189() {}

    /** RenderItem.renderItemModelTransform: the display context of the item drawn inside it (null for the GUI and plain calls). */
    public static void context(ItemCameraTransforms.TransformType type) { context = type; }

    /** RenderItem.renderModel: reads the matrices once for this model's quad lists, or turns culling off for it. */
    public static void begin() {
        active = BuiltInRender.items && GlState189.cull();
        if (!active) return;
        MODEL_VIEW.clear(); PROJECTION.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        MODEL_VIEW.get(MV); PROJECTION.get(P);
        Minecraft mc = Minecraft.getMinecraft();
        FaceCull.clip(MATRIX, P, MV, mc.displayWidth, mc.displayHeight);
        boolean far = (context == ItemCameraTransforms.TransformType.GROUND || context == ItemCameraTransforms.TransformType.FIXED)
            && MV[12] * MV[12] + MV[13] * MV[13] + MV[14] * MV[14] > LOD_DISTANCE * LOD_DISTANCE;
        lod = far ? LOD_PIXELS : 0;
    }

    /** RenderItem.renderQuads: the list without the quads the GPU would not draw (the same list when none). */
    public static List<BakedQuad> filter(List<BakedQuad> quads) {
        if (!active || quads.isEmpty()) return quads;
        List<BakedQuad> out = null;
        for (int i = 0, n = quads.size(); i < n; i++) {
            BakedQuad quad = quads.get(i);
            int[] vertices = quad.getVertexData();
            int verdict = vertices.length < 12 ? FaceCull.KEEP : FaceCull.test(MATRIX, vertices, vertices.length / 4, lod);
            if (verdict == FaceCull.KEEP) {
                BuiltInRender.quadsKept++;
                if (out != null) out.add(quad);
                continue;
            }
            if (verdict == FaceCull.CULL) BuiltInRender.quadsCulled++; else BuiltInRender.quadsLod++;
            if (out == null) {
                out = KEPT;
                out.clear();
                for (int j = 0; j < i; j++) out.add(quads.get(j));
            }
        }
        return out == null ? quads : out;
    }
}
