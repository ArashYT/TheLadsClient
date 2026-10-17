package com.thelads.core.v1_20_1.game.survival.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only (ParityProbe1201): the two protected Soul Speed steps the game runs when a living entity changes block. */
@Mixin(LivingEntity.class)
public interface SoulSpeedQaInvoker1201 {
    @Invoker("tryAddSoulSpeed")
    void lads$tryAddSoulSpeed();

    @Invoker("removeSoulSpeed")
    void lads$removeSoulSpeed();
}
