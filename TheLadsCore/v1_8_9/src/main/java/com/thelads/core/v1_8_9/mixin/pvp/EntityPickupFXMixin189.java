package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.particle.EntityPickupFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.7 Animations: 1.7 linear item pickup trajectory directly toward player. */
@Mixin(EntityPickupFX.class)
public abstract class EntityPickupFXMixin189 {
    @ModifyVariable(method = "renderParticle", at = @At(value = "STORE", ordinal = 1), ordinal = 0, require = 0)
    private float ladsPickupLinear(float f) {
        if (PvpVisuals.pickupAnim && f > 0.0f) {
            return (float) Math.sqrt(f);
        }
        return f;
    }
}
