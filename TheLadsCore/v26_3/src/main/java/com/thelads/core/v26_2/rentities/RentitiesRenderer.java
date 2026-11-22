package com.thelads.core.v26_2.rentities;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.thelads.core.modules.ExtraModules;
import com.thelads.core.modules.RentitiesModule;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rentities for 26.3 (experimental). Living-entity models whose mesh was scanned are not submitted to the vanilla per-entity
 * vertex path; their bone matrices, tint, light and overlay are collected per frame and each (render type, model) pair is
 * drawn with ONE instanced draw call at the end of the solid entity phase. Differences from the original mod: no raw GL, no
 * SSBO and no GL 4.5, the animation is the game's own (setupAnim on the CPU, one matrix per model part) and only the vertex
 * transform runs on the GPU; the fragment shader and all lighting, fog and overlay math are vanilla's.
 * Anything that is not reproduced exactly (see {@link #record}) stays on the vanilla path.
 */
public final class RentitiesRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final boolean DEBUG = Boolean.getBoolean("lads.rentities.debug");
    private static final boolean ANY_GPU = Boolean.getBoolean("lads.rentities.anyGpu");
    /** Texels per bone in the bone buffer: 4 pose matrix columns + 3 normal matrix columns. */
    private static final int BONE_TEXELS = 7;
    private static final int BONE_FLOATS = BONE_TEXELS * 4;
    /** Bones per draw, far under the 65536-texel minimum a texel buffer must hold (8000 * 7 = 56000). */
    private static final int MAX_BONES_PER_DRAW = 8000;
    private static final int INSTANCE_BYTES = 16;
    private static final String[] KIND_NAMES = {"solid", "cutout_cull", "cutout"};
    /** Frames whose instance and bone buffers are kept apart, so a frame never writes a buffer the GPU may still read. */
    private static final int RING = 3;
    private static final long MAX_BONE_BYTES = (long) MAX_BONES_PER_DRAW * BONE_FLOATS * 4;
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris") && !Boolean.getBoolean("lads.rentities.ignoreiris");

    private static final VertexFormat MESH_FORMAT = VertexFormat.builder(0)
        .addAttribute("Position", GpuFormat.RGB32_FLOAT)
        .addAttribute("UV0", GpuFormat.RG32_FLOAT)
        .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
        .addAttribute("Bone", GpuFormat.R32_SINT)
        .build();
    private static final VertexFormat INSTANCE_FORMAT = VertexFormat.builder(1)
        .addAttribute("Color", GpuFormat.RGBA8_UNORM)
        .addAttribute("UV1", GpuFormat.RG16_SINT)
        .addAttribute("UV2", GpuFormat.RG16_SINT)
        .addAttribute("InstBase", GpuFormat.R32_SINT)
        .build();
    private static final BindGroupLayout BONES_LAYOUT = BindGroupLayout.builder()
        .withUniform("RentitiesBones", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
        .build();

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[3];

    private static final class Key {
        RenderType type;
        RentitiesMesh mesh;
        Key(RenderType type, RentitiesMesh mesh) { this.type = type; this.mesh = mesh; }
        @Override public boolean equals(Object o) { return o instanceof Key k && k.mesh == mesh && Objects.equals(k.type, type); }
        @Override public int hashCode() { return System.identityHashCode(mesh) * 31 + Objects.hashCode(type); }
    }

    /** Kept across frames with its arrays; count and prepared belong to the frame in {@code frame}. */
    private static final class Batch {
        PreparedRenderType prepared;
        final RentitiesMesh mesh;
        final int kind;
        float[] bones = new float[0];
        int[] instances = new int[0];
        int count;
        long frame = -1;
        Batch(RentitiesMesh mesh, int kind) { this.mesh = mesh; this.kind = kind; }
    }

    private static final Map<Key, Batch> BATCHES = new HashMap<>();
    /** This frame's batches in first-use order. Lookups reuse PROBE; a Key is made only for a new batch. */
    private static final List<Batch> ACTIVE = new ArrayList<>();
    private static final Key PROBE = new Key(null, null);
    private static final GpuBuffer[][] RING_INST = new GpuBuffer[RING][0], RING_BONES = new GpuBuffer[RING][0];
    private static ByteBuffer stageInst = ByteBuffer.allocateDirect(0), stageBones = ByteBuffer.allocateDirect(0);
    private static FloatBuffer stageBonesF = stageBones.asFloatBuffer();
    private static long frame;
    private static int ring;
    private static boolean uploaded; // this frame's batches are in their buffers: later submits draw as vanilla
    private static boolean shaderPack;
    private static final Matrix3f NORMAL = new Matrix3f();
    private static final Quaternionf ROTATION = new Quaternionf();

    private static boolean gateDone;
    private static boolean disabled;
    private static String disabledReason = "";
    private static boolean announced;
    private static boolean inLevel;
    private static String driverInfo = "";
    private static String vendor = "";
    private static boolean glBackend;
    private static long dbgAt;
    private static String dbgSkip = "";

    private static long statsStart;
    private static int statEntities, statFallbacks, statDraws, lastEntities, lastDraws;
    private static long cacheCountAt;
    private static int cacheCount;

    private RentitiesRenderer() {}

    /** Called once at startup: cache folder, the Delete Mesh Cache button and the status line. */
    public static void init() {
        RentitiesMeshes.directory(new File(FabricLoader.getInstance().getConfigDir().toFile(), "theladscore/rentities"));
        RentitiesModule m = ExtraModules.RENTITIES;
        m.deleteCache.setAction(() -> {
            RentitiesMeshes.reset();
            int n = RentitiesMeshes.deleteFiles();
            cacheCountAt = 0;
            LOG.info("[Lads] Rentities: deleted {} cached meshes", n);
        });
        m.status.setLabel(RentitiesRenderer::statusText);
    }

    static void warn(String message) { LOG.warn("[Lads] Rentities: {}", message); }

    private static String statusText() {
        if (disabled) return "Off: " + disabledReason;
        if (!ExtraModules.RENTITIES.isEnabled()) return "Module off";
        long now = System.currentTimeMillis();
        if (now - cacheCountAt > 2000) { cacheCountAt = now; cacheCount = RentitiesMeshes.cachedFiles(); }
        if (ExtraModules.RENTITIES.scanMode.get()) return "Scanning, " + cacheCount + " meshes saved (entities draw as vanilla)";
        return cacheCount + " meshes cached, " + lastEntities + " entities in " + lastDraws + " draws last frame";
    }

    private static void disable(String reason) {
        if (disabled) return;
        disabled = true;
        disabledReason = reason;
        BATCHES.clear();
        ACTIVE.clear();
        LOG.warn("[Lads] Rentities disabled: {}", reason);
    }

    /** First use: checks the device once. Returns false (and disables for the session) when Rentities must not run. */
    private static boolean gate() {
        if (gateDone) return !disabled;
        gateDone = true;
        try {
            var info = RenderSystem.getDevice().getDeviceInfo();
            vendor = String.valueOf(info.vendorName());
            driverInfo = String.valueOf(info.driverInfo());
            String backend = String.valueOf(info.backendName());
            glBackend = backend.toLowerCase(Locale.ROOT).contains("gl");
            if (!glBackend) { disable("graphics backend is " + backend + ", not OpenGL"); return false; }
            if (!ANY_GPU && !(vendor + " " + info.name()).toLowerCase(Locale.ROOT).contains("nvidia")) {
                disable("GPU is " + vendor + ", Rentities is tested on NVIDIA only (override: -Dlads.rentities.anyGpu=true)");
                return false;
            }
            for (int i = 0; i < PIPELINES.length; i++) PIPELINES[i] = build(i);
            for (RenderPipeline p : PIPELINES) {
                if (RenderSystem.getCompiledPipelineNullable(p) == null) { disable("the instanced shader " + p.getLocation() + " did not compile"); return false; }
            }
            return true;
        } catch (Throwable t) {
            disable("setup failed: " + t);
            return false;
        }
    }

    private static RenderPipeline build(int kind) {
        var b = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("theladscore", "pipeline/rentities_" + KIND_NAMES[kind]))
            .withVertexShader(Identifier.fromNamespaceAndPath("theladscore", "core/rentities"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(BONES_LAYOUT)
            .withVertexBinding(0, MESH_FORMAT)
            .withVertexBinding(1, INSTANCE_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT);
        if (kind >= 1) b.withShaderDefine("ALPHA_CUTOUT", 0.1f);
        if (kind == 2) b.withShaderDefine("PER_FACE_LIGHTING").withCull(false);
        return b.build();
    }

    private static int kindOf(RenderPipeline p) {
        if (p == RenderPipelines.ENTITY_SOLID) return 0;
        if (p == RenderPipelines.ENTITY_CUTOUT_CULL) return 1;
        if (p == RenderPipelines.ENTITY_CUTOUT) return 2;
        return -1;
    }

    public static void levelStart() {
        inLevel = true;
        frame++;
        uploaded = false;
        ACTIVE.clear();
        shaderPack = IRIS && shaderPackInUse();
    }

    public static void levelEnd() {
        inLevel = false;
        ACTIVE.clear();
    }

    /** A shader pack replaces the entity shaders, so Rentities steps aside while one is on; Iris without a pack is fine. */
    private static boolean shaderPackInUse() {
        try {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Tries to take one model submit out of the vanilla path. True: it was recorded for an instanced draw (the caller must
     * skip vanilla). False: nothing was touched and vanilla draws it.
     */
    @SuppressWarnings("unchecked")
    public static <S> boolean record(ModelFeatureRenderer.Submit<S> submit) {
        if (disabled || !inLevel || shaderPack || uploaded) return false;
        RentitiesModule module = ExtraModules.RENTITIES;
        if (!module.isEnabled()) return false;
        try {
            if (!gate()) return false;
            S state = submit.state();
            RenderType type = submit.renderType();
            int kind = type == null ? -1 : kindOf(type.pipeline());
            if (!(state instanceof LivingEntityRenderState living) || state instanceof AvatarRenderState || state instanceof ArmorStandRenderState
                || kind < 0 || type.isOutline() || type.hasBlending() || submit.sheetedDecalPose() != null || submit.uvMapping() != null
                || living.appearsGlowing() || living.isInvisible || living.isInvisibleToPlayer) {
                statFallbacks++;
                if (DEBUG) dbgSkip = state.getClass().getSimpleName() + " type=" + type + " kind=" + kind;
                return false;
            }
            Model<S> model = (Model<S>) submit.model();
            boolean scan = module.scanMode.get();
            RentitiesMeshes.Entry entry = RentitiesMeshes.entry(model, scan);
            if (scan || entry.mesh == null) {
                statFallbacks++;
                return false;
            }
            PROBE.type = type;
            PROBE.mesh = entry.mesh;
            Batch batch = BATCHES.get(PROBE);
            PROBE.type = null;
            PROBE.mesh = null;
            if (batch == null) {
                if (BATCHES.size() >= 1024) BATCHES.clear(); // deleted meshes or many one-off render types; ACTIVE keeps this frame's
                batch = new Batch(entry.mesh, kind);
                BATCHES.put(new Key(type, entry.mesh), batch);
            }
            if (batch.frame != frame) {
                batch.frame = frame;
                batch.count = 0;
                PreparedRenderType prepared = type.prepare();
                batch.prepared = prepared.scissorState().enabled() || prepared.textures().isEmpty() ? null : prepared;
                if (batch.prepared != null) ACTIVE.add(batch);
            }
            if (batch.prepared == null) {
                statFallbacks++;
                return false;
            }
            model.setupAnim(state);
            int boneFloats = entry.mesh.boneCount * BONE_FLOATS;
            int need = (batch.count + 1) * boneFloats;
            if (batch.bones.length < need) batch.bones = Arrays.copyOf(batch.bones, Math.max(need, batch.bones.length * 2));
            int needInst = (batch.count + 1) * 3;
            if (batch.instances.length < needInst) batch.instances = Arrays.copyOf(batch.instances, Math.max(needInst, batch.instances.length * 2));
            writeBones(entry, submit.pose().pose(), batch.bones, batch.count * boneFloats);
            int tint = submit.tintedColor();
            batch.instances[batch.count * 3] = (tint & 0xFF00FF00) | ((tint >> 16) & 0xFF) | ((tint & 0xFF) << 16);
            batch.instances[batch.count * 3 + 1] = submit.overlayCoords();
            batch.instances[batch.count * 3 + 2] = submit.lightCoords();
            batch.count++;
            statEntities++;
            return true;
        } catch (Throwable t) {
            disable("error while recording an entity: " + t);
            return false;
        }
    }

    /** The bone chain, exactly ModelPart.render's: invisible or empty parts hide their whole subtree, skipDraw hides only the part. */
    private static void writeBones(RentitiesMeshes.Entry e, Matrix4f base, float[] out, int offset) {
        for (int i = 0; i < e.parts.length; i++) {
            ModelPart p = e.parts[i];
            int parent = e.parent[i];
            boolean hidden = (parent >= 0 && e.hidden[parent]) || !p.visible || (p.cubes.isEmpty() && p.children.isEmpty());
            e.hidden[i] = hidden;
            int o = offset + i * BONE_FLOATS;
            if (hidden) { Arrays.fill(out, o, o + BONE_FLOATS, 0f); continue; }
            Matrix4f w = e.world[i];
            w.set(parent >= 0 ? e.world[parent] : base);
            w.translate(p.x / 16.0F, p.y / 16.0F, p.z / 16.0F);
            if (p.xRot != 0.0F || p.yRot != 0.0F || p.zRot != 0.0F) w.rotate(ROTATION.rotationZYX(p.zRot, p.yRot, p.xRot));
            if (p.xScale != 1.0F || p.yScale != 1.0F || p.zScale != 1.0F) w.scale(p.xScale, p.yScale, p.zScale);
            if (p.skipDraw || p.cubes.isEmpty()) { Arrays.fill(out, o, o + BONE_FLOATS, 0f); continue; }
            w.get(out, o);
            NORMAL.set(w);
            float det = NORMAL.determinant();
            if (Float.isNaN(det) || Float.isInfinite(det) || Math.abs(det) < 1.0e-12F) { Arrays.fill(out, o, o + BONE_FLOATS, 0f); continue; }
            NORMAL.invert().transpose();
            int n = o + 16;
            out[n] = NORMAL.m00(); out[n + 1] = NORMAL.m01(); out[n + 2] = NORMAL.m02(); out[n + 3] = 0f;
            out[n + 4] = NORMAL.m10(); out[n + 5] = NORMAL.m11(); out[n + 6] = NORMAL.m12(); out[n + 7] = 0f;
            out[n + 8] = NORMAL.m20(); out[n + 9] = NORMAL.m21(); out[n + 10] = NORMAL.m22(); out[n + 11] = 0f;
        }
    }

    /**
     * After the frame's submits are prepared and before any render pass opens (a buffer write inside an open pass is refused):
     * this frame's instances and bones go into the ring buffers, one slot per draw.
     */
    public static void upload() {
        if (!inLevel || disabled) return;
        uploaded = true;
        if (ACTIVE.isEmpty()) return;
        ring = (ring + 1) % RING;
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            int slot = 0;
            for (int b = 0; b < ACTIVE.size(); b++) {
                Batch batch = ACTIVE.get(b);
                RentitiesMesh mesh = batch.mesh;
                int perDraw = Math.max(1, MAX_BONES_PER_DRAW / mesh.boneCount);
                int boneFloats = mesh.boneCount * BONE_FLOATS;
                for (int start = 0; start < batch.count; start += perDraw, slot++) {
                    int c = Math.min(perDraw, batch.count - start);
                    int instBytes = c * INSTANCE_BYTES, boneBytes = c * boneFloats * 4;
                    if (stageInst.capacity() < instBytes) stageInst = ByteBuffer.allocateDirect(grow(instBytes, Integer.MAX_VALUE)).order(ByteOrder.nativeOrder());
                    if (stageBones.capacity() < boneBytes) {
                        stageBones = ByteBuffer.allocateDirect(grow(boneBytes, MAX_BONE_BYTES)).order(ByteOrder.nativeOrder());
                        stageBonesF = stageBones.asFloatBuffer();
                    }
                    ByteBuffer inst = stageInst.clear();
                    for (int j = 0; j < c; j++) {
                        int s = (start + j) * 3;
                        inst.putInt(batch.instances[s]).putInt(batch.instances[s + 1]).putInt(batch.instances[s + 2]).putInt(j * mesh.boneCount);
                    }
                    inst.flip();
                    stageBonesF.clear().put(batch.bones, start * boneFloats, c * boneFloats);
                    ByteBuffer bones = stageBones.clear().limit(boneBytes);
                    GpuBuffer instBuf = ringBuffer(RING_INST, slot, instBytes, Integer.MAX_VALUE, "Lads Rentities instances", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST);
                    GpuBuffer boneBuf = ringBuffer(RING_BONES, slot, boneBytes, MAX_BONE_BYTES, "Lads Rentities bones", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST);
                    encoder.writeToBuffer(instBuf.slice(0, instBytes), inst);
                    encoder.writeToBuffer(boneBuf.slice(0, boneBytes), bones);
                }
            }
        } catch (Throwable t) {
            disable("error while uploading: " + t);
        }
    }

    /** End of the solid entity phase: one instanced draw per (render type, model) batch, from the buffers {@link #upload} filled. */
    public static void draw(RenderPass pass) {
        if (DEBUG && ExtraModules.RENTITIES.isEnabled()) {
            long t = System.currentTimeMillis();
            if (t - dbgAt >= 3000) { dbgAt = t; LOG.info("[Lads] Rentities debug: inLevel={} disabled={} batches={} recorded={} fallbacks={} lastSkip={}", inLevel, disabled, ACTIVE.size(), statEntities, statFallbacks, dbgSkip); }
        }
        if (ACTIVE.isEmpty()) return;
        int batches = ACTIVE.size();
        if (disabled) { ACTIVE.clear(); return; }
        if (!uploaded) { ACTIVE.clear(); disable("the instance upload hook did not run"); return; }
        int entities = 0, draws = 0;
        // glGetError makes the CPU wait for the driver: only on the first drawn frame (proves the path once) and with the debug flag.
        boolean checkGl = glBackend && (DEBUG || !announced);
        boolean grouped = false;
        try {
            if (checkGl) org.lwjgl.opengl.GL11C.glGetError();
            pass.pushDebugGroup(() -> "Lads Rentities");
            grouped = true;
            int slot = 0;
            for (int b = 0; b < batches; b++) {
                Batch batch = ACTIVE.get(b);
                CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(PIPELINES[batch.kind]);
                if (compiled == null) throw new IllegalStateException("pipeline lost");
                RentitiesMesh mesh = batch.mesh;
                int perDraw = Math.max(1, MAX_BONES_PER_DRAW / mesh.boneCount);
                for (int start = 0; start < batch.count; start += perDraw, slot++) {
                    int c = Math.min(perDraw, batch.count - start);
                    pass.setPipeline(compiled);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", batch.prepared.dynamicTransforms());
                    pass.setVertexBuffer(0, mesh.vertexBuffer().slice());
                    pass.setVertexBuffer(1, RING_INST[ring][slot].slice());
                    for (PreparedRenderType.Texture t : batch.prepared.textures()) pass.setUniform(t.name(), t.textureView(), t.sampler());
                    pass.setUniform("RentitiesBones", RING_BONES[ring][slot]);
                    pass.setIndexBuffer(mesh.indexBuffer(), IndexType.INT);
                    pass.drawIndexed(mesh.indices.length, c, 0, 0, 0);
                    draws++;
                    entities += c;
                }
            }
            grouped = false;
            pass.popDebugGroup();
            ACTIVE.clear();
            if (checkGl) {
                int err = org.lwjgl.opengl.GL11C.glGetError();
                if (err != 0) disable("OpenGL error 0x" + Integer.toHexString(err) + " after the instanced draw");
            }
        } catch (Throwable t) {
            if (grouped) pass.popDebugGroup(); // an open debug group fails the whole frame
            ACTIVE.clear();
            disable("error while drawing: " + t);
            return;
        }
        lastEntities = entities;
        lastDraws = draws;
        statDraws += draws;
        if (!announced) {
            announced = true;
            LOG.info("[Lads] Rentities active: {} entity types batched, GL {}, {}", batches, driverInfo, vendor);
        }
        if (DEBUG) {
            long now = System.currentTimeMillis();
            if (statsStart == 0) statsStart = now;
            if (now - statsStart >= 5000) {
                LOG.info("[Lads] Rentities: {} entities batched, {} fallbacks, {} draw calls in the last {} ms", statEntities, statFallbacks, statDraws, now - statsStart);
                statsStart = now;
                statEntities = 0;
                statFallbacks = 0;
                statDraws = 0;
            }
        }
    }

    /** Room for {@code need} bytes with headroom, never above {@code cap} (the bone texel buffer limit). */
    private static int grow(int need, long cap) { return (int) Math.min(cap, Math.max(need, Math.max(16384, (long) need * 3 / 2))); }

    /** This frame's buffer for the slot, replaced by a bigger one when too small. A slot is reused only RING frames later. */
    private static GpuBuffer ringBuffer(GpuBuffer[][] ringSet, int slot, int bytes, long cap, String label, int usage) {
        GpuBuffer[] row = ringSet[ring];
        if (slot >= row.length) ringSet[ring] = row = Arrays.copyOf(row, slot + 8);
        GpuBuffer b = row[slot];
        if (b == null || b.isClosed() || b.size() < bytes) {
            if (b != null && !b.isClosed()) b.close();
            b = row[slot] = RenderSystem.getDevice().createBuffer(() -> label, usage, grow(bytes, cap));
        }
        return b;
    }
}
