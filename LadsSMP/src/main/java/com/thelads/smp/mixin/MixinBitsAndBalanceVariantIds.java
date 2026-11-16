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

/** Same remembered ids as {@link MixinBitsAndBalanceSlabStepIds}, for the vertical slab and vertical step registries (same per-call regex work). */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.registry.VerticalStepDynamicRegistry", remap = false)
public class MixinBitsAndBalanceVariantIds {
    @Unique private static final Map<String, Map<Identifier, Identifier>> ladssmp$ids = new ConcurrentHashMap<>();

    @Inject(method = "idForVerticalSlab", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cachedId(Identifier slabId, String modId, CallbackInfoReturnable<Identifier> cir) {
        Map<Identifier, Identifier> byMod = ladssmp$ids.get(modId);
        Identifier id = byMod == null ? null : byMod.get(slabId);
        if (id != null) cir.setReturnValue(id);
    }

    @Inject(method = "idForVerticalSlab", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$storeId(Identifier slabId, String modId, CallbackInfoReturnable<Identifier> cir) {
        if (cir.getReturnValue() != null) ladssmp$ids.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).putIfAbsent(slabId, cir.getReturnValue());
    }
}
