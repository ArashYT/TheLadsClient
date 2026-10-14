package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.compat.ForgeModLoadLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps AbstractRegistrate's internal Guava Tables, Multimaps, and Sets with synchronized wrappers upon construction
 * so concurrent Create / Create addon registration during parallel Forge CONSTRUCT cannot drop entries or corrupt tables.
 */
@Pseudo
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate", remap = false)
public abstract class RegistrateSynchronizedMixin {
    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("TAIL"), remap = false, require = 0)
    private void lads$synchronizeRegistrateCollections(String modid, CallbackInfo ci) {
        ForgeModLoadLock.synchronizeRegistrateFields(this);
    }

    @Inject(method = "getModEventBus", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$resolveOwnerModEventBus(CallbackInfoReturnable<Object> cir) {
        Object bus = ForgeModLoadLock.resolveModEventBus(this);
        if (bus != null) {
            cir.setReturnValue(bus);
        }
    }
}
