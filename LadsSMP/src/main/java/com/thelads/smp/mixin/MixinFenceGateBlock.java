package com.thelads.smp.mixin;

import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'doubledoors': Synchronizes connected adjacent fence gates when toggled by a non-sneaking player.
 */
@Mixin(FenceGateBlock.class)
public abstract class MixinFenceGateBlock {

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void ladssmp$onFenceGateToggled(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && (player == null || !player.isShiftKeyDown())) {
            BlockState updated = level.getBlockState(pos);
            if (updated.hasProperty(FenceGateBlock.OPEN)) {
                ConsolidatedFeatures.tryTogglePairedFenceGate(level, pos, updated, updated.getValue(FenceGateBlock.OPEN));
            }
        }
    }
}
