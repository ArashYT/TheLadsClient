package com.thelads.smp.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bits and Balance's wood stonecutting generator calls hasRegisteredItemNamespace() for each wood family, and every call walks the
 * whole item registry (23,000 items) twice when the namespace is absent: 30 s of the creation freeze on a slow PC. The answer only
 * changes when the registry size does, so remember it per namespace and registry size.
 */
@Pseudo
@Mixin(targets = "org.onenonly.bitsandbalance.common.recipe.WoodStonecuttingRecipeGenerator", remap = false)
public class MixinBitsAndBalanceNamespaceScan {
    @Unique private static final Map<String, Boolean> ladssmp$seen = new ConcurrentHashMap<>();

    @Inject(method = "hasRegisteredItemNamespace", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$cached(String namespace, CallbackInfoReturnable<Boolean> cir) {
        Boolean known = ladssmp$seen.get(BuiltInRegistries.ITEM.size() + "|" + namespace);
        if (known != null) cir.setReturnValue(known);
    }

    @Inject(method = "hasRegisteredItemNamespace", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$store(String namespace, CallbackInfoReturnable<Boolean> cir) {
        ladssmp$seen.put(BuiltInRegistries.ITEM.size() + "|" + namespace, cir.getReturnValue());
    }
}
