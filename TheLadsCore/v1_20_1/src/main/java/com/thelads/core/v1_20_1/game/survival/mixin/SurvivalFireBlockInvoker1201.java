package com.thelads.core.v1_20_1.game.survival.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FireBlock.class)
public interface SurvivalFireBlockInvoker1201 {
    @Invoker("setFlammable")
    void lads$invokeSetFlammable(Block block, int encouragement, int flammability);

    /** QA only: how likely fire is to catch on the block (0 = not flammable). */
    @Invoker("getIgniteOdds")
    int lads$igniteOdds(BlockState state);
}
