/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.text2speech.NarratorMac
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.disablenarrator;

import com.mojang.text2speech.NarratorMac;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={NarratorMac.class})
public class NoMacNarratorMixin {
    @Inject(method={"<init>"}, at={@At(value="INVOKE", target="Lca/weblite/objc/NSObject;<init>(Ljava/lang/String;)V", shift=At.Shift.AFTER, remap=false)}, cancellable=true)
    private void onInit(CallbackInfo ci) {
        ci.cancel();
    }
}

