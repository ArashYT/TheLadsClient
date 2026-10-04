package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v1_8_9.feature.CustomFov189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom FOV: the player's FOV modifier (getFovModifier), with flying, the speed parts and the bow kept at their shares. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @ModifyConstant(method = "getFovModifier", constant = @Constant(floatValue = 1.1F), require = 1)
    private float ladsFlying(float change) {
        return CustomFov189.scaled(change, CustomFovModule.FLYING);
    }

    @Redirect(method = "getFovModifier", require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/attributes/IAttributeInstance;getAttributeValue()D"))
    private double ladsSpeed(IAttributeInstance attribute) {
        return CustomFov189.speed(attribute);
    }

    /** The bow's pull narrows the FOV by up to this much. */
    @ModifyConstant(method = "getFovModifier", constant = @Constant(floatValue = 0.15F), require = 1)
    private float ladsBow(float narrowing) {
        return narrowing * (float) CustomFov189.share(CustomFovModule.BOW);
    }
}
