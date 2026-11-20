package com.thelads.core.v1_20_1.ladsapi.minigame.world.mixin;

import com.thelads.core.v1_20_1.ladsapi.minigame.world.GhostRender1201;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A minigame ghost is drawn with the see-through render type and a half-transparent bluish tint, and without a vanilla name tag (the
 * flag is set only around its draw).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MgGhostOpacityMixin1201 {
    private static final String TO_BUFFER = "Lnet/minecraft/client/model/EntityModel;renderToBuffer("
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"), index = 2)
    private boolean lads$ghostSeeThrough(boolean translucent) {
        return translucent || GhostRender1201.rendering;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = TO_BUFFER), index = 4)
    private float lads$ghostRed(float red) {
        return GhostRender1201.rendering ? 0.66f : red;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = TO_BUFFER), index = 5)
    private float lads$ghostGreen(float green) {
        return GhostRender1201.rendering ? 0.78f : green;
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = TO_BUFFER), index = 7)
    private float lads$ghostAlpha(float alpha) {
        return GhostRender1201.rendering ? 0.5f : alpha;
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void lads$ghostNoTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (GhostRender1201.rendering) cir.setReturnValue(false);
    }
}