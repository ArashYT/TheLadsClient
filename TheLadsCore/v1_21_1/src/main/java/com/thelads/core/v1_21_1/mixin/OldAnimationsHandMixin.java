package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7 Animations, first person: the 1.7 hand replaces renderArmWithItem after its push (Iris's HEAD pass filter runs first),
 * Low Shield and the QA draw record at renderItem, and No attack-cooldown dip in tick.
 */
@Mixin(ItemInHandRenderer.class)
public class OldAnimationsHandMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER),
        cancellable = true, require = 1)
    private void lads$oldHand(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                              PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!NativeOldAnimations.firstPerson((ItemInHandRenderer) (Object) this, player, partial, hand, swing, stack, equip, pose, buffers, light)) return;
        pose.popPose();
        ci.cancel();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$item(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose, MultiBufferSource buffers,
                           int light, CallbackInfo ci) {
        NativeOldAnimations.lowShield(context, stack, pose);
        if (NativeOldAnimations.recorded(context, pose)) ci.cancel();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 1)
    private float lads$noCooldownDip(float scale) {
        // Legacy Swing keeps the item up too (LegacySwingMixin holds its other hooks).
        return com.thelads.core.v1_21_1.feature.NativeQualityOfLife.enabled("LegacySwing") ? 1 : NativeOldAnimations.equipScale(scale);
    }
}
