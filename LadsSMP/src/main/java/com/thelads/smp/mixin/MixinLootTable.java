package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/**
 * Pathway 7: Wraps the Consumer<ItemStack> in LootTable#getRandomItemsRaw so disabled or loot-disabled items
 * can never be emitted by any vanilla or modded loot table (chests, Lootr, archeology, vaults, fishing, mob loot).
 */
@Mixin(LootTable.class)
public abstract class MixinLootTable {

    @ModifyVariable(
            method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Consumer<ItemStack> ladssmp$wrapLootOutputConsumer(Consumer<ItemStack> originalConsumer, LootContext context) {
        return stack -> {
            if (stack != null && !stack.isEmpty() && ItemDisablerSystem.isDisabledForLoot(stack)) {
                return;
            }
            originalConsumer.accept(stack);
        };
    }
}
