package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.attachments;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WitchItemLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.thelads.core.v26_2.embedded.emf.models.IEMFModel;
import com.thelads.core.v26_2.embedded.emf.models.animation.EMFAttachment;

@Mixin(CrossedArmsItemLayer.class)
public abstract class Mixin_Villager_BlockOffset extends RenderLayer {

    public Mixin_Villager_BlockOffset() {
        super(null);
    }

    @WrapWithCondition(method = "applyTranslation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/VillagerLikeModel;translateToArms(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 0))
    private boolean offsetBlock(VillagerLikeModel instance, net.minecraft.client.renderer.entity.state.EntityRenderState entityRenderState, PoseStack poseStack) {

        var model = (IEMFModel) getParentModel();
        if (model.emf$isEMFModel()) {
            var root = model.emf$getEMFRootModel();
            var positioner = root.getPositionerForAttachment(EMFAttachment.Type.VILLAGER);
            if (positioner != null) {
                positioner.accept(poseStack);
                return false;
            }
        }
        return true;
    }

}