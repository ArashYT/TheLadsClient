package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'leavesbegone': Accelerates leaf decay (5-20 ticks) once leaves become randomly ticking after log removal.
 */
@Mixin(LeavesBlock.class)
public abstract class MixinLeavesBlock {

    @Shadow
    protected abstract void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladssmp$onLeafTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!LadsConfig.get().features.fastLeafDecay) return;
        BlockState updated = level.getBlockState(pos);
        if (updated.is((LeavesBlock) (Object) this) && updated.isRandomlyTicking()) {
            if (state.isRandomlyTicking()) {
                this.randomTick(updated, level, pos, random);
            } else {
                level.scheduleTick(pos, (LeavesBlock) (Object) this, 5 + random.nextInt(15));
            }
        }
    }
}
