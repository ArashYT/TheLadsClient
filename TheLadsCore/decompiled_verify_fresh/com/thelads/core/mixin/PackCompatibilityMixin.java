/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.packs.metadata.pack.PackFormat
 *  net.minecraft.server.packs.repository.PackCompatibility
 *  net.minecraft.util.InclusiveRange
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin;

import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.InclusiveRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PackCompatibility.class})
public class PackCompatibilityMixin {
    @Inject(method={"forVersion"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private static void ladsAlwaysCompatible(InclusiveRange<PackFormat> range, PackFormat current, CallbackInfoReturnable<PackCompatibility> cir) {
        cir.setReturnValue((Object)PackCompatibility.COMPATIBLE);
    }
}

