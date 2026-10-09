package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.anim.LegacySwing1201;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first-person hand: Legacy Swing's swing, no attack-cooldown dip and no pop after using an item (Legacy Swing, 1.7 Animations'
 * No attack-cooldown dip), and 1.7 Animations' item placement at the item draw.
 */
@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void lads$legacySwing(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo callback) {
        if (LegacySwing1201.apply(pose, swing, arm == HumanoidArm.RIGHT ? 1 : -1)) callback.cancel();
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
    private float lads$noCooldownDip(float scale) {
        return NativeOldAnimations1201.equipScale(scale);
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void lads$noUsePop(InteractionHand hand, CallbackInfo callback) {
        if (LegacySwing1201.enabled()) callback.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void lads$context(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand, float swing, ItemStack stack,
                              float equip, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        NativeOldAnimations1201.enter(hand, partial, swing, equip);
    }

    // 1.7 Animations: the flat item drawn in 1.7's placement with no display transform, no shield while the sword blocks.
    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;"
        + "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
        + "ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), require = 2)
    private void lads$oldItem(ItemInHandRenderer self, LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean left,
                              PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        ItemDisplayContext drawn = NativeOldAnimations1201.firstPerson(entity, stack, pose, context);
        if (drawn != null) original.call(self, entity, stack, drawn, left, pose, buffers, light);
    }
}
