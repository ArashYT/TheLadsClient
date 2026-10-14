package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.compat.ForgeModLoadLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces ForgeEventsRemapper's unsynchronized Object2ObjectOpenHashMap fields with ConcurrentHashMap so parallel
 * Zeta / Quark / Supplementaries event remapping cannot corrupt internal lookup tables.
 */
@Pseudo
@Mixin(targets = "org.violetmoon.zetaimplforge.event.ForgeEventsRemapper", remap = false)
public abstract class ZetaRemapperSynchronizedMixin {
    @Inject(method = "<init>(Ljava/lang/Class;Ljava/lang/Class;)V", at = @At("TAIL"), remap = false, require = 0)
    private void lads$synchronizeRemapperMaps(Class<?> zetaRoot, Class<?> forgeRoot, CallbackInfo ci) {
        ForgeModLoadLock.synchronizeMapFields(
            this,
            "org.violetmoon.zetaimplforge.event.ForgeEventsRemapper",
            "forgeToZetaMap",
            "zetaToForgeMap",
            "generics",
            "zetaToForgeEventClassHack"
        );
    }
}
