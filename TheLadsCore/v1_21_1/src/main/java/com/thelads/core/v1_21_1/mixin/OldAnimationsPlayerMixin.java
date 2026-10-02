package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.v1_21_1.feature.NativeOldAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 third person: the shield-triggered sword block poses the sword arm, not the shield arm. */
@Mixin(PlayerRenderer.class)
public class OldAnimationsPlayerMixin {
    @Inject(method = "getArmPose", at = @At("RETURN"), cancellable = true, require = 1)
    private static void lads$swordBlockPose(AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        cir.setReturnValue(NativeOldAnimations.armPose(player, hand, cir.getReturnValue()));
    }
}
