package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Pathway 8: Prevents disabled items from dropping when blocks are broken or exploded.
 */
@Mixin(Block.class)
public abstract class MixinBlockDrops {

    @Inject(
            method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ladssmp$blockDisabledBlockDrop(Level level, Supplier<ItemEntity> entitySupplier, ItemStack stack, CallbackInfo ci) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private static void ladssmp$dropStickyEnchantingLapis(
            net.minecraft.world.level.block.state.BlockState state,
            Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity,
            net.minecraft.world.entity.Entity entity,
            ItemStack tool,
            CallbackInfo ci
    ) {
        if (!com.thelads.smp.config.LadsConfig.get().features.stickyEnchantingLapis) return;
        if (!level.isClientSide() && blockEntity instanceof net.minecraft.world.level.block.entity.EnchantingTableBlockEntity etbe) {
            int lapisCount = com.thelads.smp.features.StickyLapisManager.getLapisCount(etbe);
            if (lapisCount > 0) {
                Block.popResource(level, pos, new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, lapisCount));
                com.thelads.smp.features.StickyLapisManager.saveLapisCount(level, etbe, 0);
            }
        }
    }
}
