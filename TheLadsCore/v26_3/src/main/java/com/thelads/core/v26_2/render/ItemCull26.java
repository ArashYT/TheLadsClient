package com.thelads.core.v26_2.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.FaceCull;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Lads Flerovium, items (1.8.5): ItemFeatureRenderer skips the baked quads the GPU would cull anyway (back faces, a flat item's edge
 * strips seen square-on) and far dropped or framed items' sub-pixel quads (LOD). 26.3 keeps the projection only on the GPU, so the
 * CPU copy of each projection matrix is remembered when ProjectionMatrixBuffer uploads it and followed through
 * RenderSystem.setProjectionMatrix; with an unknown projection nothing is culled. Only for render pipelines that cull, outside
 * Iris shadow passes.
 */
public final class ItemCull26 {
    private static final float LOD_DISTANCE = 32, LOD_PIXELS = 0.25f;
    private static final Map<GpuBuffer, Matrix4f> UPLOADED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final MethodHandle SHADOW_PASS = bindShadowPass();
    private static final Matrix4f SCRATCH = new Matrix4f();
    private static final float[] MATRIX = new float[FaceCull.MATRIX_FLOATS];
    private static @Nullable Matrix4f projection, backup;
    private static @Nullable Object lastSubmit;
    private static boolean usable;
    private static float lod;

    private ItemCull26() {}

    /** ProjectionMatrixBuffer.writeBuffer: the CPU copy of what goes into {@code buffer}. */
    public static void uploaded(GpuBuffer buffer, Matrix4fc matrix) {
        Matrix4f copy = UPLOADED.get(buffer);
        if (copy == null) UPLOADED.put(buffer, new Matrix4f(matrix)); else copy.set(matrix);
    }

    public static void projection(GpuBufferSlice slice) { projection = UPLOADED.get(slice.buffer()); lastSubmit = null; }
    public static void backup() { backup = projection; }
    public static void restore() { projection = backup; lastSubmit = null; }

    /** A new feature group: the same submit object may come back under other matrices. */
    public static void newGroup() { lastSubmit = null; }

    /** Whether ItemFeatureRenderer should write this quad of {@code submit} (its pose and display context) with {@code renderType}. */
    public static boolean keep(Object submit, PoseStack.Pose pose, ItemDisplayContext context, RenderType renderType, BakedQuad quad) {
        if (!BuiltInRender.items || !renderType.pipeline().isCull()) return true;
        if (submit != lastSubmit) {
            lastSubmit = submit;
            usable = projection != null && !shadowPass();
            if (usable) {
                Matrix4f m = pose.pose();
                var window = Minecraft.getInstance().getWindow();
                SCRATCH.set(projection).mul(RenderSystem.getModelViewStack()).mul(m).get(MATRIX);
                FaceCull.prepare(FaceCull.toViewport(MATRIX, window.getWidth(), window.getHeight()));
                boolean far = (context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED)
                    && m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32() > LOD_DISTANCE * LOD_DISTANCE;
                lod = far ? LOD_PIXELS : 0;
            }
        }
        if (!usable) return true;
        Vector3fc a = quad.position0(), b = quad.position1(), c = quad.position2(), d = quad.position3();
        int verdict = FaceCull.test(MATRIX, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), c.x(), c.y(), c.z(), d.x(), d.y(), d.z(), lod);
        if (verdict == FaceCull.KEEP) { BuiltInRender.quadsKept++; return true; }
        if (verdict == FaceCull.CULL) BuiltInRender.quadsCulled++; else BuiltInRender.quadsLod++;
        return false;
    }

    private static boolean shadowPass() {
        if (SHADOW_PASS == null) return false;
        try { return (boolean) SHADOW_PASS.invoke(); } catch (Throwable failure) { return true; }
    }

    /** Iris: IrisApi.getInstance().isRenderingShadowPass(), bound once; null without it. */
    private static @Nullable MethodHandle bindShadowPass() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (Throwable absent) { return null; }
    }
}
