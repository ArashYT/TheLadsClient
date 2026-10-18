package com.thelads.smp.features.arise.nb10.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.smp.features.arise.nb10.AriseNB10Config;
import com.thelads.smp.features.arise.nb10.DecorationType;
import com.thelads.smp.features.arise.nb10.FastenerBlockEntity;
import com.thelads.smp.features.arise.nb10.HangingConnection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, zero-per-frame-allocation renderer for hanging fairy lights,
 * pennant bunting, and garlands in Minecraft 26.3.
 */
public class FastenerBlockEntityRenderer implements BlockEntityRenderer<FastenerBlockEntity, FastenerRenderState> {

    public FastenerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public FastenerRenderState createRenderState() {
        return new FastenerRenderState();
    }

    @Override
    public void extractRenderState(
            FastenerBlockEntity be,
            FastenerRenderState state,
            float partialTick,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay overlay
    ) {
        state.clear();
        int maxDist = AriseNB10Config.get().renderDistance;
        if (!be.shouldRenderAtDistance(cameraPos, maxDist)) {
            state.culled = true;
            return;
        }

        List<HangingConnection> conns = be.getConnections();
        if (conns.isEmpty()) {
            return;
        }

        // Retrieve or generate cached meshes
        @SuppressWarnings("unchecked")
        List<CachedConnectionMesh> cachedList = (List<CachedConnectionMesh>) be.getClientCachedMesh();
        if (cachedList == null || cachedList.size() != conns.size()) {
            cachedList = new ArrayList<>(conns.size());
            BlockPos origin = be.getBlockPos();

            for (HangingConnection conn : conns) {
                BlockPos target = conn.getTargetPos();
                double relX = (target.getX() - origin.getX());
                double relY = (target.getY() - origin.getY());
                double relZ = (target.getZ() - origin.getZ());

                CachedConnectionMesh mesh = CachedConnectionMesh.build(
                        0.5, 0.5, 0.5,
                        relX + 0.5, relY + 0.5, relZ + 0.5,
                        conn.getSlack(),
                        conn.getType(),
                        conn.getColors()
                );
                cachedList.add(mesh);
            }
            be.setClientCachedMesh(cachedList);
        }

        state.meshes.addAll(cachedList);
        for (HangingConnection conn : conns) {
            state.types.add(conn.getType());
        }

        state.windTime = (be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : 0.0F);
        state.windSwayEnabled = AriseNB10Config.get().windSway;
    }

    @Override
    public void submit(
            FastenerRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraRenderState
    ) {
        if (state.culled || state.meshes.isEmpty()) {
            return;
        }

        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), (pose, consumer) -> {
            Matrix4f matrix = pose.pose();
            float windTime = state.windTime;
            boolean sway = state.windSwayEnabled;

            for (int m = 0; m < state.meshes.size(); m++) {
                CachedConnectionMesh mesh = state.meshes.get(m);
                DecorationType type = state.types.get(m);

                float dx = mesh.x[mesh.segments] - mesh.x[0];
                float dz = mesh.z[mesh.segments] - mesh.z[0];
                float horizDist = (float) Math.sqrt(dx * dx + dz * dz);
                float nx = horizDist > 0.01F ? -dz / horizDist : 0.0F;
                float nz = horizDist > 0.01F ? dx / horizDist : 0.0F;

                // 1. Render the hanging string / cord
                renderCord(consumer, matrix, mesh, type, nx, nz, windTime, sway, m);

                // 2. Render light bulbs / pennants / leaves along the cord
                renderOrnaments(consumer, matrix, mesh, type, nx, nz, windTime, sway, m);
            }
        });
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

            float sway0 = sway ? (float) Math.sin(Math.PI * t0) * (float) Math.sin(0.06F * windTime + seed) * 0.08F : 0.0F;
            float sway1 = sway ? (float) Math.sin(Math.PI * t1) * (float) Math.sin(0.06F * windTime + seed) * 0.08F : 0.0F;

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
            int seed
    ) {
        int emissiveLight = LightCoordsUtil.FULL_BRIGHT;

        for (int i = 0; i < mesh.bulbCount; i++) {
            float t = (float) (i + 1) / (mesh.bulbCount + 1);
            float swayOffset = sway ? (float) Math.sin(Math.PI * t) * (float) Math.sin(0.06F * windTime + seed) * 0.08F : 0.0F;

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

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return AriseNB10Config.get().renderDistance;
    }
}
