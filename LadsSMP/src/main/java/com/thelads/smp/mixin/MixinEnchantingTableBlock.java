package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.features.StickyLapisManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Consolidates 'stickyenchantinglapis': Records the Enchanting Table position interacted with by a player.
 */
@Mixin(EnchantingTableBlock.class)
public abstract class MixinEnchantingTableBlock {

    @Inject(
            method = "useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD")
    )
    private void ladssmp$recordEnchantingTableInteraction(
            BlockState blockState,
            Level level,
            BlockPos blockPos,
            Player player,
            BlockHitResult blockHitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (LadsConfig.get().features.stickyEnchantingLapis) {
            StickyLapisManager.recordInteraction(player, blockPos);
        }
    }
}
