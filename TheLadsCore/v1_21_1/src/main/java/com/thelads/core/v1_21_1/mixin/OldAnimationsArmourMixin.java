package com.thelads.core.v1_21_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Red armour on hurt: the armour and trim passes take the body's hurt overlay instead of none. */
@Mixin(HumanoidArmorLayer.class)
public class OldAnimationsArmourMixin {
    @Unique private int lads$overlay = OverlayTexture.NO_OVERLAY;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), require = 1)
    private void lads$redArmour(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing, float limbSwingAmount,
                                float partial, float age, float headYaw, float headPitch, CallbackInfo ci) {
        lads$overlay = NativeOldAnimations.armourOverlay(entity);
    }

    @ModifyArg(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
        index = 3, require = 1)
    private int lads$armourOverlay(int overlay) {
        return NativeOldAnimations.armourLayer(lads$overlay);
    }

    @ModifyArg(method = "renderTrim", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"),
        index = 3, require = 1)
    private int lads$trimOverlay(int overlay) {
        return NativeOldAnimations.armourLayer(lads$overlay);
    }
}
