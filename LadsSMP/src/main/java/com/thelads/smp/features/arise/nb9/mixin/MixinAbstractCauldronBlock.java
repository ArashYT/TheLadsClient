package com.thelads.smp.features.arise.nb9.mixin;

import com.thelads.smp.features.arise.nb9.CauldronInteractionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on AbstractCauldronBlock to intercept dye dipping and potion brewing before default interactions.
 */
@Mixin(AbstractCauldronBlock.class)
public abstract class MixinAbstractCauldronBlock {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void ladssmp$handleCauldronUse(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        InteractionResult res = CauldronInteractionHelper.handleWaterCauldron(stack, state, level, pos, player, hand);
        if (res != InteractionResult.PASS) {
            cir.setReturnValue(res);
        }
    }
}
