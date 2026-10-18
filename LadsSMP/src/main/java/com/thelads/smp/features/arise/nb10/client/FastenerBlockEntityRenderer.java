package com.thelads.smp.features.arise.nb10.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb10.AriseNB10Config;
import com.thelads.smp.features.arise.nb10.FastenerBlock;
import com.thelads.smp.features.arise.nb10.FastenerBlockEntity;
import com.thelads.smp.features.arise.nb10.HangingConnection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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

    private static double getAnchorOffsetX(Direction face) {
        if (face == Direction.WEST) return 0.125;
        if (face == Direction.EAST) return 0.875;
        return 0.5;
    }

    private static double getAnchorOffsetY(Direction face) {
        if (face == Direction.DOWN) return 0.875;
        if (face == Direction.UP) return 0.125;
        return 0.5;
    }

    private static double getAnchorOffsetZ(Direction face) {
        if (face == Direction.NORTH) return 0.125;
        if (face == Direction.SOUTH) return 0.875;
        return 0.5;
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
        if (!AriseNB10Config.get().enabled) {
            state.culled = true;
            return;
        }

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
            Direction originFacing = be.getBlockState().getValue(FastenerBlock.FACING);

            for (HangingConnection conn : conns) {
                BlockPos target = conn.getTargetPos();
                double relX = (target.getX() - origin.getX());
                double relY = (target.getY() - origin.getY());
                double relZ = (target.getZ() - origin.getZ());
                Direction targetFace = conn.getTargetFace();

                double startX = getAnchorOffsetX(originFacing);
                double startY = getAnchorOffsetY(originFacing);
                double startZ = getAnchorOffsetZ(originFacing);

                double endX = relX + getAnchorOffsetX(targetFace);
                double endY = relY + getAnchorOffsetY(targetFace);
                double endZ = relZ + getAnchorOffsetZ(targetFace);

                CachedConnectionMesh mesh = CachedConnectionMesh.build(
                        startX, startY, startZ,
                        endX, endY, endZ,
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
        state.windSwayAmplitude = AriseNB10Config.get().windSwayAmplitude;
        state.windSwaySpeed = AriseNB10Config.get().windSwaySpeed;
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

        // Zero-allocation submission: state directly implements CustomGeometryRenderer
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), state);
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
