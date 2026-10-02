package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.thelads.core.v26_2.feature.LegacySwing;
import com.thelads.core.v26_2.feature.NativeOldAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    // Legacy Swing: no attack-cooldown dip and no pop after using an item. Switching items still lowers and raises the hand.
    // 1.7 Animations' No attack-cooldown dip keeps the item up the same way.
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"), require = 1)
    private float ladsNoCooldownDip(float scale) {
        return com.thelads.core.v26_2.feature.NativeQualityOfLife.enabled("LegacySwing") ? 1 : NativeOldAnimations.equipScale(scale);
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNoUsePop(net.minecraft.world.InteractionHand hand, CallbackInfo ci) {
        if (com.thelads.core.v26_2.feature.NativeQualityOfLife.enabled("LegacySwing")) ci.cancel();
    }

    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsConsoleSwing(float progress,PoseStack pose,int side,HumanoidArm arm,CallbackInfo ci){
        if(LegacySwing.apply(pose,progress,side))ci.cancel();
    }

    // 1.7 Animations (and Low Shield) at the item draw: the 1.7 placement drawn with no display transform, or no shield
    // while the sword blocks. LegacySwing's turn above stays for items drawn in vanilla's placement.
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
        + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"), require = 1)
    private void ladsOldAnimations(ItemInHandRenderer renderer, LivingEntity entity, ItemStack item, ItemDisplayContext context, PoseStack pose,
                                   SubmitNodeCollector collector, int light, Operation<Void> original, @Local(argsOnly = true) InteractionHand hand,
                                   @Local(argsOnly = true, ordinal = 0) float partial, @Local(argsOnly = true, ordinal = 2) float swing,
                                   @Local(argsOnly = true, ordinal = 3) float equip) {
        ItemDisplayContext drawn = NativeOldAnimations.firstPerson(entity, hand, item, partial, swing, equip, pose, context);
        if (drawn != null) original.call(renderer, entity, item, drawn, pose, collector, light);
    }
}
