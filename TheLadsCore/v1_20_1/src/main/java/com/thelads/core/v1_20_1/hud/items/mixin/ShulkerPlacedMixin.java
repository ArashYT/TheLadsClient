package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockItem.place calls this right after the block is set, before the stack shrinks. On the client vanilla never copies the
 * item's BlockEntityTag into the predicted block entity (there is no server there), so a placed box learns its contents here.
 */
@Mixin(BlockItem.class)
abstract class ShulkerPlacedMixin {
    @Inject(method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("HEAD"))
    private void lads$placed(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (level.isClientSide()) ShulkerContents1201.placed(level, pos, stack);
    }
}
