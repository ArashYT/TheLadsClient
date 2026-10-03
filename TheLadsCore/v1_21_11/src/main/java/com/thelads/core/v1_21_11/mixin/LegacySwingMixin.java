package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_21_11.feature.LegacySwing;
import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Swing, as 26.x LegacySwingMixin: the Legacy Console Edition swing replaces swingArm (vanilla's swing translation and
 * turn) and using an item no longer pops the hand down. No attack-cooldown dip shares OldAnimationsHandMixin's tick hook; items
 * 1.7 Animations places get Legacy Swing in NativeOldAnimations.firstPerson.
 */
@Mixin(ItemInHandRenderer.class)
public class LegacySwingMixin {
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$legacySwing(float progress, PoseStack pose, int side, HumanoidArm arm, CallbackInfo ci) {
        if (LegacySwing.apply(pose, progress, side)) ci.cancel();
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$noUsePop(InteractionHand hand, CallbackInfo ci) {
        if (NativeQualityOfLife.enabled("LegacySwing")) ci.cancel();
    }
}
