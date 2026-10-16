package com.thelads.core.v26_2.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FireBlock.class)
public interface SurvivalFireBlockInvoker {
    @Invoker("setFlammable")
    void lads$invokeSetFlammable(Block block, int encouragement, int flammability);
}
