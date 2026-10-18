package com.thelads.smp.features.arise.nb8.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb8.client.AriseNB8Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injects into LevelRenderer to draw wireframe outline previews around all connected blocks
 * while the vein mining key is actively held down.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererBlockOutline {

    @Inject(
            method = "submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At("TAIL")
    )
    private void ladssmp$renderVeinMiningOutlines(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LevelRenderState levelRenderState,
            CallbackInfo ci) {

        if (!AriseNB8Client.isVeinMiningActive()) {
            return;
        }

        List<BlockPos> previewBlocks = AriseNB8Client.getPreviewBlocks();
        if (previewBlocks.isEmpty()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Vec3 camPos = levelRenderState.cameraRenderState.pos;

        for (BlockPos p : previewBlocks) {
            poseStack.pushPose();
            poseStack.translate(p.getX() - camPos.x, p.getY() - camPos.y, p.getZ() - camPos.z);
            VoxelShape shape = level.getBlockState(p).getShape(level, p);
            submitNodeCollector.submitShapeOutline(
                    poseStack,
                    shape,
                    RenderTypes.linesTranslucent(),
                    0xFFFFFFFF,
                    1.5F,
                    false
            );
            poseStack.popPose();
        }
    }
}
