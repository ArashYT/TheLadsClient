package com.thelads.core.v1_20_1.game.survival.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA only (ParityProbe1201): blows a creeper up without waiting for its fuse. */
@Mixin(Creeper.class)
public interface CreeperQaInvoker1201 {
    @Invoker("explodeCreeper")
    void lads$explodeCreeper();
}
