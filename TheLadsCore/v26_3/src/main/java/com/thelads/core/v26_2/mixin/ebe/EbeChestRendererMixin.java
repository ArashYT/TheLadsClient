package com.thelads.core.v26_2.mixin.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dlads.ebe.xmas=true|false forces the Christmas chest texture for the vanilla renderer too (pixel-diff QA). */
@Mixin(ChestRenderer.class)
public abstract class EbeChestRendererMixin {
    @Inject(method = "xmasTextures", at = @At("HEAD"), cancellable = true)
    private static void lads$ebeForcedXmas(CallbackInfoReturnable<Boolean> cir) {
        if (BakedBlockEntities.christmasForced()) cir.setReturnValue(BakedBlockEntities.christmas());
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void lads$openedChestSubmit(net.minecraft.client.renderer.blockentity.state.ChestRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (state.blockPos != null && com.thelads.core.client.hypixel.OpenedChestHighlight.isOpened(state.blockPos.getX(), state.blockPos.getY(), state.blockPos.getZ())) {
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(state.blockPos.getX() + 0.0625, state.blockPos.getY(), state.blockPos.getZ() + 0.0625, state.blockPos.getX() + 0.9375, state.blockPos.getY() + 0.875, state.blockPos.getZ() + 0.9375);
            net.minecraft.gizmos.Gizmos.cuboid(box, net.minecraft.gizmos.GizmoStyle.stroke(0xCC00FF00, 2.0f));
        }
    }
}
