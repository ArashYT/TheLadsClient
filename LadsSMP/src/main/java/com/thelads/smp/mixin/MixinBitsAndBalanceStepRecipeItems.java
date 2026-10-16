package com.thelads.smp.mixin;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;

/**
 * Bits & Balance 2.4.1 rebuilds the slab/step item sets for every step recipe while recipes load: each call walks every
 * block, and for each one that is not a step walks every slab again through a regex. With this pack that stalls the server
 * thread for 80-160 s on every world load and /reload (enough for a dedicated server's 60 s watchdog to kill it).
 * Both sets only depend on the block registry, which is frozen by then, so compute them once.
 */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.recipe.StepFromSlabRecipe", remap = false)
public class MixinBitsAndBalanceStepRecipeItems {
    @Unique private static LinkedHashSet<Item> ladssmp$stepItems, ladssmp$slabItems;

    @Inject(method = "stepPlacementItems", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cachedStepItems(CallbackInfoReturnable<LinkedHashSet<Item>> cir) {
        if (ladssmp$stepItems != null) cir.setReturnValue(new LinkedHashSet<>(ladssmp$stepItems));
    }

    @Inject(method = "stepPlacementItems", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$storeStepItems(CallbackInfoReturnable<LinkedHashSet<Item>> cir) {
        if (ladssmp$stepItems == null) ladssmp$stepItems = new LinkedHashSet<>(cir.getReturnValue());
    }

    @Inject(method = "slabPlacementItems", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cachedSlabItems(CallbackInfoReturnable<LinkedHashSet<Item>> cir) {
        if (ladssmp$slabItems != null) cir.setReturnValue(new LinkedHashSet<>(ladssmp$slabItems));
    }

    @Inject(method = "slabPlacementItems", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$storeSlabItems(CallbackInfoReturnable<LinkedHashSet<Item>> cir) {
        if (ladssmp$slabItems == null) ladssmp$slabItems = new LinkedHashSet<>(cir.getReturnValue());
    }
}
