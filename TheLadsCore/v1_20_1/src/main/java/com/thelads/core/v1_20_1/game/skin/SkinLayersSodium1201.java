package com.thelads.core.v1_20_1.game.skin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * 1.9.12 perf: the skin-layer cuboids written straight into Sodium's vertex buffer (its VertexBufferWriter API, Sodium 0.5), the way
 * Sodium writes vanilla's own model cuboids. The generic 14-argument VertexConsumer.vertex was the hottest Java method of the
 * 1.20.1 first-person view in the Legion bench (the sleeves are drawn every frame). Only loaded when Sodium is installed.
 */
final class SkinLayersSodium1201 {
    /** Quads per push: 128 x 4 vertices x ModelVertex.STRIDE bytes stays well inside LWJGL's per-thread MemoryStack. */
    private static final int BATCH = 128;
    private static final Vector3f POS = new Vector3f(), NORMAL = new Vector3f();

    private SkinLayersSodium1201() {}

    /** False when this consumer is not one of Sodium's (the caller then writes the vertices itself). Render thread only. */
    static boolean draw(float[] quads, Matrix4f matrix, Matrix3f normals, VertexConsumer out, int light, int overlay) {
        VertexBufferWriter writer = VertexBufferWriter.tryOf(out);
        if (writer == null) return false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(BATCH * 4 * ModelVertex.STRIDE), ptr = buffer;
            int count = 0;
            for (int q = 0; q < quads.length; q += VoxelMesh.FLOATS) {
                normals.transform(quads[q + 14], quads[q + 15], quads[q + 16], NORMAL);
                int normal = NormI8.pack(NORMAL.x(), NORMAL.y(), NORMAL.z());
                float u = quads[q + 12], v = quads[q + 13];
                for (int c = 0; c < 12; c += 3) {
                    matrix.transformPosition(quads[q + c], quads[q + c + 1], quads[q + c + 2], POS);
                    ModelVertex.write(ptr, POS.x(), POS.y(), POS.z(), 0xFFFFFFFF, u, v, overlay, light, normal);
                    ptr += ModelVertex.STRIDE;
                }
                if (++count == BATCH) {
                    writer.push(stack, buffer, count * 4, ModelVertex.FORMAT);
                    ptr = buffer;
                    count = 0;
                }
            }
            if (count > 0) writer.push(stack, buffer, count * 4, ModelVertex.FORMAT);
        }
        return true;
    }
}
