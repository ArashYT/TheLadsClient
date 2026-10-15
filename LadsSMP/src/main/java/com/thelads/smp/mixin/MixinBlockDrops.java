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
}
