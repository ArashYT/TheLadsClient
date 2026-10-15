package com.thelads.smp.mixin;

import com.thelads.smp.features.ConsolidatedFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'doubledoors': Synchronizes paired adjacent wooden doors when toggled by a non-sneaking player.
 */
@Mixin(DoorBlock.class)
public abstract class MixinDoorBlock {

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void ladssmp$onDoorToggled(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && (player == null || !player.isShiftKeyDown())) {
            BlockState updated = level.getBlockState(pos);
            if (updated.hasProperty(DoorBlock.OPEN)) {
                ConsolidatedFeatures.tryTogglePairedDoor(level, pos, updated, updated.getValue(DoorBlock.OPEN));
            }
        }
    }
}
