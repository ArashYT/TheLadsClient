package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.particle.ItemPickupParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.7 Animations: 1.7 linear item pickup trajectory directly toward player. */
@Mixin(ItemPickupParticle.class)
public abstract class ItemPickupParticleMixin26 {
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 1), ordinal = 0, require = 0)
    private float lads$linearPickup(float g) {
        if (PvpVisuals.pickupAnim && g > 0.0f) {
            return (float) Math.sqrt(g);
        }
        return g;
    }
}
