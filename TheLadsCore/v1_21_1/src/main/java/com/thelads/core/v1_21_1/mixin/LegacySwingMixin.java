package com.thelads.core.v1_21_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_1.feature.LegacySwing;
import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Swing, as 26.x LegacySwingMixin: the Legacy Console Edition swing in place of vanilla's, its translation too, and no pop
 * after using an item. 1.21.1 translates by vanilla's swing offset just before applyItemArmTransform and turns in
 * applyItemArmAttackTransform, so the turn's HEAD takes the offset back and plays Legacy Swing instead. No attack-cooldown dip
 * shares OldAnimationsHandMixin's tick hook; items 1.7 Animations places get Legacy Swing in NativeOldAnimations.firstPerson.
 */
@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$legacySwing(PoseStack pose, HumanoidArm arm, float progress, CallbackInfo ci) {
        if (!NativeQualityOfLife.enabled("LegacySwing") || progress <= 0) return;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float root = Mth.sqrt(progress);
        pose.translate(side * 0.4f * Mth.sin(root * Mth.PI), -0.2f * Mth.sin(root * Mth.PI * 2), 0.2f * Mth.sin(progress * Mth.PI));
        LegacySwing.apply(pose, progress, side);
        ci.cancel();
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$noUsePop(InteractionHand hand, CallbackInfo ci) {
        if (NativeQualityOfLife.enabled("LegacySwing")) ci.cancel();
    }
}
