package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * Legacy Swing: no attack-cooldown dip and no pop after using an item, as on Legacy Console. Switching items still lowers and raises the hand.
 * 1.7 Animations' No attack-cooldown dip keeps the item up the same way.
 */
@Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
public class LegacyEquipMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"), require = 1)
    private float ladsNoCooldownDip(float scale) {
        return NativeQualityOfLife.enabled("LegacySwing") ? 1 : com.thelads.core.v26_2.feature.NativeOldAnimations.equipScale(scale);
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNoUsePop(InteractionHand hand, CallbackInfo ci) {
        if (NativeQualityOfLife.enabled("LegacySwing")) ci.cancel();
    }
}
