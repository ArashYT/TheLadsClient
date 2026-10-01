package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.attachments;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.WitchItemLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.embedded.emf.models.IEMFModel;
import com.thelads.core.v26_2.embedded.emf.models.animation.EMFAttachment;

@Mixin(WitchItemLayer.class)
public abstract class Mixin_Witch_BlockOffset extends CrossedArmsItemLayer {

    public Mixin_Witch_BlockOffset() {
        super(null);
    }

    private static final String RENDER_METHOD = "applyTranslation(Lnet/minecraft/client/renderer/entity/state/WitchRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V";

    @WrapWithCondition(method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 0))
    private boolean offsetBlock(ModelPart instance, PoseStack arg, @Local(argsOnly = true) PoseStack poseStack, @Share("needsCancel") LocalBooleanRef needsCancel) {
        var model = (IEMFModel) getParentModel();
        if (model.emf$isEMFModel()) {
            var root = model.emf$getEMFRootModel();
            var positioner = root.getPositionerForAttachment(EMFAttachment.Type.WITCH);
            if (positioner != null) {
                positioner.accept(poseStack);
                needsCancel.set(true);
                return false;
            }
        }
        return true;
    }

    @WrapWithCondition(method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 1))
    private boolean cancel(ModelPart instance, PoseStack arg, @Share("needsCancel") LocalBooleanRef needsCancel) {
        return !needsCancel.get();
    }

    @WrapWithCondition(method = RENDER_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/monster/witch/WitchModel;translateToHead(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private boolean cancel2(WitchModel instance, PoseStack poseStack, @Share("needsCancel") LocalBooleanRef needsCancel) {
        return !needsCancel.get();
    }
}