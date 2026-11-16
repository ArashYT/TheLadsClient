package com.thelads.smp.mixin;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bits and Balance's EnhancedSlabRecipeLookup.slabForStep() asks idForSlab() for every slab once per block it is asked about, and
 * idForSlab() compiles two regexes per call. Building the step recipes (every world creation, load and /reload) therefore ran about
 * 30 million regex matches on the thread that creates the integrated server: a 55-88 s freeze of the window on a slow PC, 5 s on a fast
 * one. idForSlab gives the same id every time for the same slab (after its first call the registry already holds it), so remember it
 * per mod id (always "bitsandbalance" in practice) and slab id.
 */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.registry.StepDynamicRegistry", remap = false)
public class MixinBitsAndBalanceSlabStepIds {
    @Unique private static final Map<String, Map<Identifier, Identifier>> ladssmp$ids = new ConcurrentHashMap<>();

    @Inject(method = "idForSlab", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cachedId(Identifier slabId, String modId, CallbackInfoReturnable<Identifier> cir) {
        Map<Identifier, Identifier> byMod = ladssmp$ids.get(modId);
        Identifier id = byMod == null ? null : byMod.get(slabId);
        if (id != null) cir.setReturnValue(id);
    }

    @Inject(method = "idForSlab", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$storeId(Identifier slabId, String modId, CallbackInfoReturnable<Identifier> cir) {
        if (cir.getReturnValue() != null) ladssmp$ids.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).putIfAbsent(slabId, cir.getReturnValue());
    }
}
