package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.attachments;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.embedded.emf.models.IEMFModel;
import com.thelads.core.v26_2.embedded.emf.models.animation.EMFAttachment;

@Mixin(FoxHeldItemLayer.class)
public abstract class Mixin_Fox_BlockOffset extends RenderLayer {

    public Mixin_Fox_BlockOffset() {
        super(null);
    }

    @WrapWithCondition(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/FoxRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private boolean offsetBlock(PoseStack instance, float f, float g, float h, @Local(argsOnly = true) PoseStack poseStack, @Share("cancelRestOfHead") LocalBooleanRef cancelRestOfHead) {
        var model = (IEMFModel) getParentModel();
        if (model.emf$isEMFModel()) {
            var root = model.emf$getEMFRootModel();
            var positioner = root.getPositionerForAttachment(EMFAttachment.Type.FOX);
            if (positioner != null) {
                positioner.accept(poseStack);
                cancelRestOfHead.set(true);
                return false;
            }
        }
        return true;
    }

//    @WrapWithCondition(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/FoxRenderState;FF)V",
//            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0))
//    private boolean offsetBlock2(PoseStack instance, float f, float g, float h, @Share("cancelRestOfHead") LocalBooleanRef cancelRestOfHead) {
//        return !cancelRestOfHead.get();
//    }


    private static final String MULPOSE = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V";

    @WrapWithCondition(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/FoxRenderState;FF)V",
            at = @At(value = "INVOKE", target = MULPOSE, ordinal = 0))
    private boolean offsetBlock3(PoseStack instance,
                                    Quaternionfc
                                    quaternionfc, @Share("cancelRestOfHead") LocalBooleanRef cancelRestOfHead) {
        return !cancelRestOfHead.get();
    }
    @WrapWithCondition(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/FoxRenderState;FF)V",
            at = @At(value = "INVOKE", target = MULPOSE, ordinal = 1))
    private boolean offsetBlock4(PoseStack instance,
                                 Quaternionfc
                                 quaternionfc, @Share("cancelRestOfHead") LocalBooleanRef cancelRestOfHead) {
        return !cancelRestOfHead.get();
    }
    @WrapWithCondition(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/FoxRenderState;FF)V",
            at = @At(value = "INVOKE", target = MULPOSE, ordinal = 2))
    private boolean offsetBlock5(PoseStack instance,
                                 Quaternionfc
                                 quaternionfc, @Share("cancelRestOfHead") LocalBooleanRef cancelRestOfHead) {
        return !cancelRestOfHead.get();
    }
}