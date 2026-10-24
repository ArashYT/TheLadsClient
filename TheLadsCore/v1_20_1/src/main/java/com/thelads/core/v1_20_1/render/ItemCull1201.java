package com.thelads.core.v1_20_1.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.FaceCull;
import com.thelads.core.perf.GuiItemBaking;
import com.thelads.core.perf.SodiumPipelineHook;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<RenderType, Boolean> CULLS = new IdentityHashMap<>();
    private static final Map<BakedModel, BakedModel> GUI_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
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
        if (!BuiltInRender.items || type == null || model instanceof Wrapper || model instanceof GuiCachedModel
            || !RenderSystem.isOnRenderThread() || !culls(type))
            return model;
        if (context == ItemDisplayContext.GUI) {
            if (!GuiItemBaking.isEnabled()) return model;
            BakedModel cached = GUI_CACHE.get(model);
            if (cached != null) return cached;
            if (!model.isGui3d()) {
                cached = GuiCachedModel.for2d(model);
                GUI_CACHE.put(model, cached);
                return cached;
            }
            Matrix4f pose = poseStack.last().pose();
            var window = Minecraft.getInstance().getWindow();
            SCRATCH.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
            SCRATCH.get(WRAPPER.matrix);
            FaceCull.prepare(FaceCull.toViewport(WRAPPER.matrix, window.getWidth(), window.getHeight()));
            cached = GuiCachedModel.for3d(model, WRAPPER.matrix);
            GUI_CACHE.put(model, cached);
            return cached;
        }
        if (shadowPass()) return model;
        Matrix4f pose = poseStack.last().pose();
        var window = Minecraft.getInstance().getWindow();
        SCRATCH.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        SCRATCH.get(WRAPPER.matrix);
        FaceCull.prepare(FaceCull.toViewport(WRAPPER.matrix, window.getWidth(), window.getHeight()));
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

    /** Cached GUI model wrapper for 2D flat items (only SOUTH faces visible) and 3D block items (isometric FaceCull cached once). */
    static final class GuiCachedModel implements BakedModel {
        private final BakedModel model;
        private final List<BakedQuad>[] cachedQuads;
        private final int[] keptCounts;
        private final int[] culledCounts;

        private GuiCachedModel(BakedModel model, List<BakedQuad>[] cachedQuads, int[] keptCounts, int[] culledCounts) {
            this.model = model;
            this.cachedQuads = cachedQuads;
            this.keptCounts = keptCounts;
            this.culledCounts = culledCounts;
        }

        @SuppressWarnings("unchecked")
        static GuiCachedModel for2d(BakedModel model) {
            List<BakedQuad>[] lists = new List[7];
            int[] kept = new int[7];
            int[] culled = new int[7];
            RandomSource random = RandomSource.create();
            for (int i = 0; i < DIRECTIONS.length; i++) {
                Direction dir = DIRECTIONS[i];
                random.setSeed(42L);
                List<BakedQuad> quads = model.getQuads(null, dir, random);
                if (dir == Direction.SOUTH) {
                    lists[i] = quads;
                    kept[i] = quads.size();
                } else {
                    lists[i] = Collections.emptyList();
                    culled[i] = quads.size();
                }
            }
            random.setSeed(42L);
            List<BakedQuad> unculled = model.getQuads(null, null, random);
            if (unculled.isEmpty()) {
                lists[6] = Collections.emptyList();
            } else {
                List<BakedQuad> southOnly = new ArrayList<>(unculled.size());
                for (int i = 0, n = unculled.size(); i < n; i++) {
                    BakedQuad quad = unculled.get(i);
                    if (quad.getDirection() == Direction.SOUTH) {
                        southOnly.add(quad);
                    }
                }
                lists[6] = southOnly.isEmpty() ? Collections.emptyList() : (southOnly.size() == unculled.size() ? unculled : List.copyOf(southOnly));
                kept[6] = lists[6].size();
                culled[6] = unculled.size() - kept[6];
            }
            return new GuiCachedModel(model, lists, kept, culled);
        }

        @SuppressWarnings("unchecked")
        static GuiCachedModel for3d(BakedModel model, float[] matrix) {
            List<BakedQuad>[] lists = new List[7];
            int[] kept = new int[7];
            int[] culled = new int[7];
            RandomSource random = RandomSource.create();
            for (int idx = 0; idx < 7; idx++) {
                Direction side = idx < 6 ? DIRECTIONS[idx] : null;
                random.setSeed(42L);
                List<BakedQuad> quads = model.getQuads(null, side, random);
                if (quads.isEmpty()) {
                    lists[idx] = Collections.emptyList();
                    continue;
                }
                List<BakedQuad> out = null;
                int k = 0, c = 0;
                for (int i = 0, n = quads.size(); i < n; i++) {
                    BakedQuad quad = quads.get(i);
                    int[] vertices = quad.getVertices();
                    int verdict = vertices.length < 12 ? FaceCull.KEEP : FaceCull.test(matrix, vertices, vertices.length / 4, 0f);
                    if (verdict == FaceCull.KEEP) {
                        k++;
                        if (out != null) out.add(quad);
                        continue;
                    }
                    c++;
                    if (out == null) {
                        out = new ArrayList<>(n);
                        for (int j = 0; j < i; j++) out.add(quads.get(j));
                    }
                }
                lists[idx] = out == null ? quads : (out.isEmpty() ? Collections.emptyList() : List.copyOf(out));
                kept[idx] = k;
                culled[idx] = c;
            }
            return new GuiCachedModel(model, lists, kept, culled);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            if (state != null) {
                if (!model.isGui3d()) {
                    if (side == Direction.SOUTH) return model.getQuads(state, Direction.SOUTH, random);
                    if (side != null) return Collections.emptyList();
                    List<BakedQuad> quads = model.getQuads(state, null, random);
                    List<BakedQuad> out = new ArrayList<>();
                    for (int i = 0, n = quads.size(); i < n; i++) {
                        BakedQuad q = quads.get(i);
                        if (q.getDirection() == Direction.SOUTH) out.add(q);
                    }
                    return out;
                }
                return model.getQuads(state, side, random);
            }
            int idx = side == null ? 6 : side.ordinal();
            BuiltInRender.quadsKept += keptCounts[idx];
            BuiltInRender.quadsCulled += culledCounts[idx];
            return cachedQuads[idx];
        }

        @Override public boolean useAmbientOcclusion() { return model.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return model.isGui3d(); }
        @Override public boolean usesBlockLight() { return model.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return model.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return model.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return model.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return model.getOverrides(); }
    }

    /** One reusable wrapper: renderModelLists reads each returned list fully before asking for the next. */
    static final class Wrapper implements BakedModel {
        final float[] matrix = new float[FaceCull.MATRIX_FLOATS];
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
