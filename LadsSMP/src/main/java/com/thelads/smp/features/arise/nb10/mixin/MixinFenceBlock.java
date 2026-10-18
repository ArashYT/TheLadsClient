package com.thelads.smp.features.arise.nb10.mixin;

import com.thelads.smp.features.arise.nb10.FastenerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into FenceBlock to allow detaching attached hanging light strings
 * by sneak-right-clicking or shearing the fence post.
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
        ItemStack held = player.getMainHandItem();
        boolean isShears = held.is(Items.SHEARS);
        boolean isEmptyHand = held.isEmpty();

        if (player.isShiftKeyDown() && (isEmptyHand || isShears)) {
            // Check clicked face adjacent position first, then pos.above(), then horizontal sides
            BlockPos targetPos = null;
            BlockPos clickedOffset = pos.relative(hit.getDirection());
            if (level.getBlockEntity(clickedOffset) instanceof FastenerBlockEntity) {
                targetPos = clickedOffset;
            } else if (level.getBlockEntity(pos.above()) instanceof FastenerBlockEntity) {
                targetPos = pos.above();
            } else {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = pos.relative(dir);
                    if (level.getBlockEntity(side) instanceof FastenerBlockEntity) {
                        targetPos = side;
                        break;
                    }
                }
            }

            if (targetPos != null) {
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be instanceof FastenerBlockEntity fastenerBe) {
                    if (fastenerBe.detachOne(player)) {
                        if (!level.isClientSide()) {
                            if (isShears) {
                                held.hurtAndBreak(1, player, player.getEquipmentSlotForItem(held));
                            }
                            level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
                        }
                        cir.setReturnValue(InteractionResult.SUCCESS);
                    }
                }
            }
        }
    }
}
