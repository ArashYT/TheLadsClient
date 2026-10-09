package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v1_20_1.game.NativeCustomFov1201;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Custom FOV: the player's FOV modifier, with flying, sprint and Speed/Slowness, the bow and the spyglass kept at their shares. */
@Mixin(AbstractClientPlayer.class)
public class CustomFovPlayerMixin {
    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 1.1F))
    private float lads$flying(float change) { return NativeCustomFov1201.scaled(change, CustomFovModule.FLYING); }

    @ModifyExpressionValue(method = "getFieldOfViewModifier",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D"))
    private double lads$speed(double speed) { return NativeCustomFov1201.speed((AbstractClientPlayer) (Object) this, speed); }

    /** The bow's pull narrows the FOV by up to this much. */
    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 0.15F))
    private float lads$bow(float narrowing) { return narrowing * (float) NativeCustomFov1201.share(CustomFovModule.BOW); }

    @ModifyConstant(method = "getFieldOfViewModifier", constant = @Constant(floatValue = 0.1F))
    private float lads$spyglass(float change) { return NativeCustomFov1201.scaled(change, CustomFovModule.SPYGLASS); }
}
