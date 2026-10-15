package com.thelads.smp.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Consolidates 'attributefix': Accessor to raise RangedAttribute.maxValue without generating 60+ config files.
 */
@Mixin(RangedAttribute.class)
public interface MixinRangedAttribute {
    @Accessor("maxValue")
    double ladssmp$getMaxValue();

    @Mutable
    @Accessor("maxValue")
    void ladssmp$setMaxValue(double maxValue);
}
