package com.thelads.core.v1_20_1.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.FaceCull;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Lads Flerovium, items (1.8.5): the baked item model handed to ItemRenderer.renderModelLists is wrapped so its quad lists leave out
 * the quads the GPU would cull (back faces, and a flat item's edge strips seen square-on in the GUI), and far dropped or framed items'
 * sub-pixel quads (LOD). Works under vanilla, Sodium's and Embeddium's fast item paths alike, which all read the lists through
 * getQuads. Only when the item's render type culls, outside Iris/Oculus shadow passes, on the render thread.
 */
public final class ItemCull1201 {
    /** Beyond this many blocks a dropped or framed item's quads under {@link #LOD_PIXELS} of screen area are dropped. */
    private static final float LOD_DISTANCE = 32, LOD_PIXELS = 0.25f;
    private static final Map<RenderType, Boolean> CULLS = new IdentityHashMap<>();
    private static final Wrapper WRAPPER = new Wrapper();
    private static final MethodHandle SHADOW_PASS = bindShadowPass();
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static @Nullable RenderType renderType;
    private static @Nullable ItemDisplayContext context;

    private ItemCull1201() {}

    /** ItemRenderer.render: the display context of the item being drawn. */
    public static void begin(ItemDisplayContext displayContext) { context = displayContext; renderType = null; }

    /** ItemRenderer's foil buffer helpers: the render type the next renderModelLists writes with. */
    public static void renderType(RenderType type) { renderType = type; }

    /** The model renderModelLists draws: the culling wrapper when this item can be culled exactly, else the model itself. */
    public static BakedModel wrap(BakedModel model, PoseStack poseStack) {
        RenderType type = renderType;
        renderType = null;
        if (!BuiltInRender.items || type == null || model instanceof Wrapper || !RenderSystem.isOnRenderThread() || !culls(type) || shadowPass())
            return model;
        Matrix4f pose = poseStack.last().pose();
        var window = Minecraft.getInstance().getWindow();
        SCRATCH.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        SCRATCH.get(WRAPPER.matrix);
        FaceCull.toViewport(WRAPPER.matrix, window.getWidth(), window.getHeight());
        boolean far = (context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED)
            && pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32() > LOD_DISTANCE * LOD_DISTANCE;
        WRAPPER.lod = far ? LOD_PIXELS : 0;
        WRAPPER.model = model;
        return WRAPPER;
    }

    /** Whether the GPU culls back faces for this render type (its cull state shard reads "cull[true]"; unknown wrappers do not count). */
    static boolean culls(RenderType type) {
        Boolean known = CULLS.get(type);
        if (known == null) CULLS.put(type, known = type.toString().contains("cull[true]"));
        return known;
    }

    private static boolean shadowPass() {
        if (SHADOW_PASS == null) return false;
        try { return (boolean) SHADOW_PASS.invoke(); } catch (Throwable failure) { return true; }
    }

    /** Iris / Oculus: IrisApi.getInstance().isRenderingShadowPass(), bound once; null without either. */
    private static @Nullable MethodHandle bindShadowPass() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (Throwable absent) { return null; }
    }

    /** One reusable wrapper: renderModelLists reads each returned list fully before asking for the next. */
    static final class Wrapper implements BakedModel {
        final float[] matrix = new float[16];
        final ArrayList<BakedQuad> kept = new ArrayList<>();
        BakedModel model;
        float lod;

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            List<BakedQuad> quads = model.getQuads(state, side, random);
            ArrayList<BakedQuad> out = null;
            for (int i = 0, n = quads.size(); i < n; i++) {
                BakedQuad quad = quads.get(i);
                int[] vertices = quad.getVertices();
                int verdict = vertices.length < 12 ? FaceCull.KEEP : FaceCull.test(matrix, vertices, vertices.length / 4, lod);
                if (verdict == FaceCull.KEEP) {
                    BuiltInRender.quadsKept++;
                    if (out != null) out.add(quad);
                    continue;
                }
                if (verdict == FaceCull.CULL) BuiltInRender.quadsCulled++; else BuiltInRender.quadsLod++;
                if (out == null) {
                    out = kept;
                    out.clear();
                    for (int j = 0; j < i; j++) out.add(quads.get(j));
                }
            }
            return out == null ? quads : out;
        }

        @Override public boolean useAmbientOcclusion() { return model.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return model.isGui3d(); }
        @Override public boolean usesBlockLight() { return model.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return model.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return model.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return model.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return model.getOverrides(); }
    }
}
