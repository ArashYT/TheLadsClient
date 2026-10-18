package com.thelads.smp.features.arise.nb10.mixin;

import com.thelads.smp.features.arise.nb10.FastenerBlockEntity;
import com.thelads.smp.features.arise.nb10.HangingConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin into FenceBlock to allow detaching attached hanging light strings
 * by sneak-right-clicking the fence post with an empty hand.
 */
@Mixin(FenceBlock.class)
public abstract class MixinFenceBlock {

    @Inject(
            method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ladssmp$detachFenceHangingDecorations(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            BlockPos above = pos.above();
            BlockEntity be = level.getBlockEntity(above);
            if (be instanceof FastenerBlockEntity fastenerBe) {
                List<HangingConnection> conns = fastenerBe.getConnections();
                if (!conns.isEmpty()) {
                    if (!level.isClientSide()) {
                        HangingConnection last = conns.get(conns.size() - 1);
                        fastenerBe.removeConnection(last.getId());
                        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    cir.setReturnValue(InteractionResult.SUCCESS);
                }
            }
        }
    }
}
