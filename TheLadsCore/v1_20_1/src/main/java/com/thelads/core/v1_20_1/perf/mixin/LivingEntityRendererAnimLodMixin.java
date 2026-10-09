package com.thelads.core.v1_20_1.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_20_1.perf.EntityAnimationLod1201;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAnimLodMixin {

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;prepareMobModel(Lnet/minecraft/world/entity/Entity;FFF)V"),
        require = 0
    )
    private void lads$wrapPrepareMobModel(EntityModel<?> model, Entity entity, float limbSwing, float limbSwingAmount, float partialTick, Operation<Void> original) {
        int currentTick = EntityAnimationLod1201.getClientTick();
        if (entity instanceof LivingEntity living && !EntityAnimationLod1201.shouldEvaluateModel(living, partialTick, currentTick)) {
            return;
        }
        original.call(model, entity, limbSwing, limbSwingAmount, partialTick);
    }

    @WrapOperation(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
        require = 0
    )
    private void lads$wrapSetupAnim(
        EntityModel<?> model, Entity entity, float limbSwing, float limbSwingAmount,
        float ageInTicks, float netHeadYaw, float headPitch, Operation<Void> original,
        @Local(argsOnly = true, ordinal = 1) float partialTick
    ) {
        if (entity instanceof LivingEntity living) {
            int currentTick = EntityAnimationLod1201.getClientTick();
            EntityAnimationLod1201.tickCleanup(currentTick);
            if (EntityAnimationLod1201.shouldEvaluateModel(living, partialTick, currentTick)) {
                original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                EntityAnimationLod1201.recordPose(living, model, currentTick);
                return;
            }
            EntityAnimationLod1201.applyInterpolatedPose(living, model, partialTick, currentTick);
            return;
        }
        original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
