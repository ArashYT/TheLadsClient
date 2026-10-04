package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v26_2.feature.NativeCustomFov;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Custom FOV: the player's FOV modifier, with flying, sprint and Speed/Slowness, the bow and the spyglass kept at their shares. */
@Mixin(AbstractClientPlayer.class)
public class CustomFovPlayerMixin {
    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 1.1F), require = 1)
    private float lads$flying(float change) { return NativeCustomFov.scaled(change, CustomFovModule.FLYING); }

    @ModifyExpressionValue(method = "getFieldOfViewModifier", require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double lads$speed(double speed) { return NativeCustomFov.speed((AbstractClientPlayer) (Object) this, speed); }

    /** The bow's pull narrows the FOV by up to this much. */
    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 0.15F), require = 1)
    private float lads$bow(float narrowing) { return narrowing * (float) NativeCustomFov.share(CustomFovModule.BOW); }

    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 0.1F), require = 1)
    private float lads$spyglass(float change) { return NativeCustomFov.scaled(change, CustomFovModule.SPYGLASS); }
}
