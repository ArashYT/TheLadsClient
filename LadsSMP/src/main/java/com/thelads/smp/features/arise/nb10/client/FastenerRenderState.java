package com.thelads.smp.features.arise.nb10.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.smp.features.arise.nb10.DecorationType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Render state container and zero-per-frame-allocation CustomGeometryRenderer
 * for FastenerBlockEntity in Minecraft 26.3.
 */
public class FastenerRenderState extends BlockEntityRenderState implements SubmitNodeCollector.CustomGeometryRenderer {

    public final List<CachedConnectionMesh> meshes = new ArrayList<>();
    public final List<DecorationType> types = new ArrayList<>();
    public float windTime = 0.0F;
    public boolean windSwayEnabled = true;
    public float windSwayAmplitude = 0.08F;
    public float windSwaySpeed = 0.06F;
    public boolean culled = false;

    public void clear() {
        meshes.clear();
        types.clear();
        culled = false;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer consumer) {
        Matrix4f matrix = pose.pose();

        for (int m = 0; m < meshes.size(); m++) {
            CachedConnectionMesh mesh = meshes.get(m);
            DecorationType type = types.get(m);

            float dx = mesh.x[mesh.segments] - mesh.x[0];
            float dz = mesh.z[mesh.segments] - mesh.z[0];
            float horizDist = (float) Math.sqrt(dx * dx + dz * dz);
            float nx = horizDist > 0.01F ? -dz / horizDist : 0.0F;
            float nz = horizDist > 0.01F ? dx / horizDist : 0.0F;

            // 1. Render the hanging string / cord
            renderCord(consumer, matrix, mesh, type, nx, nz, windTime, windSwayEnabled, windSwayAmplitude, windSwaySpeed, m);

            // 2. Render light bulbs / pennants along the cord
            renderOrnaments(consumer, matrix, mesh, type, nx, nz, windTime, windSwayEnabled, windSwayAmplitude, windSwaySpeed, m);
        }
    }

    private void renderCord(
            VertexConsumer consumer,
            Matrix4f matrix,
            CachedConnectionMesh mesh,
            DecorationType type,
            float nx,
            float nz,
            float windTime,
            boolean sway,
            float amplitude,
            float speed,
            int seed
    ) {
        float r = 0.15F, g = 0.15F, b = 0.15F;
        float width = 0.025F;

        if (type == DecorationType.GARLAND) {
            r = 0.18F; g = 0.38F; b = 0.12F;
            width = 0.06F;
        } else if (type == DecorationType.TINSEL) {
            r = 0.90F; g = 0.90F; b = 0.95F;
            width = 0.04F;
        }

        int light = LightCoordsUtil.FULL_BRIGHT;

        for (int i = 0; i < mesh.segments; i++) {
            float t0 = (float) i / mesh.segments;
            float t1 = (float) (i + 1) / mesh.segments;

            float sway0 = sway ? (float) Math.sin(Math.PI * t0) * (float) Math.sin(speed * windTime + seed) * amplitude : 0.0F;
            float sway1 = sway ? (float) Math.sin(Math.PI * t1) * (float) Math.sin(speed * windTime + seed) * amplitude : 0.0F;

            float x0 = mesh.x[i] + nx * sway0;
            float y0 = mesh.y[i];
            float z0 = mesh.z[i] + nz * sway0;

            float x1 = mesh.x[i + 1] + nx * sway1;
            float y1 = mesh.y[i + 1];
            float z1 = mesh.z[i + 1] + nz * sway1;

            // Quad ribbon segment
            consumer.addVertex(matrix, x0 - nx * width, y0, z0 - nz * width).setColor(r, g, b, 1.0F).setLight(light);
            consumer.addVertex(matrix, x0 + nx * width, y0 + width, z0 + nz * width).setColor(r, g, b, 1.0F).setLight(light);
            consumer.addVertex(matrix, x1 + nx * width, y1 + width, z1 + nz * width).setColor(r, g, b, 1.0F).setLight(light);
            consumer.addVertex(matrix, x1 - nx * width, y1, z1 - nz * width).setColor(r, g, b, 1.0F).setLight(light);
        }
    }

    private void renderOrnaments(
            VertexConsumer consumer,
            Matrix4f matrix,
            CachedConnectionMesh mesh,
            DecorationType type,
            float nx,
            float nz,
            float windTime,
            boolean sway,
            float amplitude,
            float speed,
            int seed
    ) {
        int emissiveLight = LightCoordsUtil.FULL_BRIGHT;

        for (int i = 0; i < mesh.bulbCount; i++) {
            float t = (float) (i + 1) / (mesh.bulbCount + 1);
            float swayOffset = sway ? (float) Math.sin(Math.PI * t) * (float) Math.sin(speed * windTime + seed) * amplitude : 0.0F;

            float bx = mesh.bulbX[i] + nx * swayOffset;
            float by = mesh.bulbY[i];
            float bz = mesh.bulbZ[i] + nz * swayOffset;

            int color = mesh.bulbColors[i];
            float cr = ((color >> 16) & 0xFF) / 255.0F;
            float cg = ((color >> 8) & 0xFF) / 255.0F;
            float cb = (color & 0xFF) / 255.0F;

            if (type == DecorationType.LIGHTS) {
                // Emissive glowing bulb: small cross quad hanging downwards
                float sz = 0.045F;
                float drop = 0.07F;
                float cy = by - drop;

                // First face
                consumer.addVertex(matrix, bx - sz, cy - sz, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx + sz, cy - sz, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx + sz, cy + sz, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx - sz, cy + sz, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);

                // Perpendicular face
                consumer.addVertex(matrix, bx, cy - sz, bz - sz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx, cy - sz, bz + sz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx, cy + sz, bz + sz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx, cy + sz, bz - sz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
            } else if (type == DecorationType.PENNANT) {
                // Triangular pennant flag hanging downwards
                float halfWidth = 0.08F;
                float dropLength = 0.16F;

                consumer.addVertex(matrix, bx - nx * halfWidth, by, bz - nz * halfWidth).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx + nx * halfWidth, by, bz + nz * halfWidth).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx, by - dropLength, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
                consumer.addVertex(matrix, bx, by - dropLength, bz).setColor(cr, cg, cb, 1.0F).setLight(emissiveLight);
            }
        }
    }
}
