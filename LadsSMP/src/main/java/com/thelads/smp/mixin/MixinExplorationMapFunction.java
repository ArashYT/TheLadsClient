package com.thelads.smp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.smp.compat.MapSearchBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gives every explorer-map loot roll a time budget (see {@link MapSearchBudget}); a search past it leaves the map blank. */
@Mixin(ExplorationMapFunction.class)
public abstract class MixinExplorationMapFunction {
    private static final Logger LADSSMP_LOGGER = LoggerFactory.getLogger("LadsSMP/MapSearch");

    @WrapMethod(method = "run")
    private ItemStack ladssmp$budgetSearch(ItemStack stack, LootContext context, Operation<ItemStack> original) {
        // Worldgen threads must never search the live world: Smart Backpacks' Backpacker Camp builds its trader's trades (an explorer
        // map among them) while its chunk generates, and that search waited on the main thread, which could be waiting on the chunk.
        if (!context.getLevel().getServer().isSameThread()) return stack;
        MapSearchBudget.start();
        try {
            return original.call(stack, context);
        } catch (MapSearchBudget.Timeout timeout) {
            LADSSMP_LOGGER.warn("Explorer map search stopped after {} ms; the map stays blank", MapSearchBudget.BUDGET_MS);
            return stack;
        } finally {
            MapSearchBudget.clear();
        }
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/MapItem;renderBiomePreviewMap(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)V"))
    private void ladssmp$skipLatePreview(ServerLevel level, ItemStack map, Operation<Void> original) {
        try {
            original.call(level, map);
        } catch (MapSearchBudget.Timeout timeout) {
            // Out of budget mid-preview: the map keeps its marker and fills in as players explore.
        }
    }
}
