package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.attachments;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.layers.DolphinCarryingItemLayer;
import net.minecraft.client.renderer.entity.layers.PandaHoldsItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.EMFAttachment;

@Mixin(PandaHoldsItemLayer.class)
public abstract class Mixin_Panda_BlockOffset extends RenderLayer {

    public Mixin_Panda_BlockOffset() {
        super(null);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Panda;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void offsetBlock(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        var model = (IEMFModel) getParentModel();
        if (model.emf$isEMFModel()) {
            var root = model.emf$getEMFRootModel();
            var positioner = root.getPositionerForAttachment(EMFAttachment.Type.DOLPHIN);
            if (positioner != null) {
                positioner.accept(poseStack);
            }
        }
    }
}