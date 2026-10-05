package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A particle's size (its quad is 0.1 x particleScale blocks), for Particles189's frustum test. */
@Mixin(EntityFX.class)
public interface EntityFXAccessor {
    @Accessor("particleScale") float ladsScale();
}
