package com.thelads.core.v26_2.rentities;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One entity model's geometry in part-local space, extracted once from its ModelPart cuboids. Every vertex carries the index of the
 * model part (bone) it belongs to; the bone matrices come from the live model every frame. Triangles, so a quad is (0,1,2)+(0,2,3),
 * the same winding vanilla's quad index buffer gives.
 */
final class RentitiesMesh {
    static final int FILE_MAGIC = 0x52454E54; // "RENT"
    static final int FILE_VERSION = 1;
    /** Mesh vertex bytes: position 3f, uv 2f, normal 3f, bone 1i. */
    static final int VERTEX_BYTES = 36;

    final long signature;
    final int boneCount;
    /** Per vertex 8 floats: x y z u v nx ny nz. */
    final float[] data;
    final int[] bones;
    final int[] indices;
    private GpuBuffer vbo;
    private GpuBuffer ibo;

    RentitiesMesh(long signature, int boneCount, float[] data, int[] bones, int[] indices) {
        this.signature = signature;
        this.boneCount = boneCount;
        this.data = data;
        this.bones = bones;
        this.indices = indices;
    }

    int vertexCount() { return bones.length; }

    GpuBuffer vertexBuffer() { upload(); return vbo; }

    GpuBuffer indexBuffer() { upload(); return ibo; }

    private void upload() {
        if (vbo != null) return;
        int n = vertexCount();
        ByteBuffer v = ByteBuffer.allocateDirect(n * VERTEX_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 8; k++) v.putFloat(data[i * 8 + k]);
            v.putInt(bones[i]);
        }
        v.flip();
        ByteBuffer ix = ByteBuffer.allocateDirect(indices.length * 4).order(ByteOrder.nativeOrder());
        for (int i : indices) ix.putInt(i);
        ix.flip();
        var device = RenderSystem.getDevice();
        vbo = device.createBuffer(() -> "Lads Rentities mesh", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, v);
        ibo = device.createBuffer(() -> "Lads Rentities mesh indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, ix);
    }

    void release() {
        if (vbo != null) { vbo.close(); vbo = null; }
        if (ibo != null) { ibo.close(); ibo = null; }
    }

    void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(file.getPath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(FILE_MAGIC);
            out.writeInt(FILE_VERSION);
            out.writeLong(signature);
            out.writeInt(boneCount);
            out.writeInt(bones.length);
            out.writeInt(indices.length);
            for (float f : data) out.writeFloat(f);
            for (int b : bones) out.writeInt(b);
            for (int i : indices) out.writeInt(i);
        }
        if (file.exists() && !file.delete()) throw new IOException("cannot replace " + file);
        if (!tmp.renameTo(file)) throw new IOException("cannot rename " + tmp);
    }

    /** The saved mesh, or null when the file is missing, from another format version, another model structure or damaged. */
    static RentitiesMesh load(File file, long expectedSignature) {
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != FILE_MAGIC || in.readInt() != FILE_VERSION || in.readLong() != expectedSignature) return null;
            int bones = in.readInt(), vertices = in.readInt(), indexCount = in.readInt();
            if (bones <= 0 || bones > 4096 || vertices <= 0 || vertices > 400_000 || indexCount <= 0 || indexCount > 2_400_000) return null;
            float[] data = new float[vertices * 8];
            for (int i = 0; i < data.length; i++) data[i] = in.readFloat();
            int[] boneOf = new int[vertices];
            for (int i = 0; i < vertices; i++) {
                boneOf[i] = in.readInt();
                if (boneOf[i] < 0 || boneOf[i] >= bones) return null;
            }
            int[] idx = new int[indexCount];
            for (int i = 0; i < indexCount; i++) {
                idx[i] = in.readInt();
                if (idx[i] < 0 || idx[i] >= vertices) return null;
            }
            return new RentitiesMesh(expectedSignature, bones, data, boneOf, idx);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
