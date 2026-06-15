package com.thelads.core.mixin;

import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.InclusiveRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the external no-resource-pack-warnings mod: every pack is treated
 * as compatible, so the "made for a different version" warnings never appear.
 */
@Mixin(PackCompatibility.class)
public class PackCompatibilityMixin {

    @Inject(method = "forVersion", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ladsAlwaysCompatible(InclusiveRange<PackFormat> range, PackFormat current,
                                             CallbackInfoReturnable<PackCompatibility> cir) {
        cir.setReturnValue(PackCompatibility.COMPATIBLE);
    }
}
