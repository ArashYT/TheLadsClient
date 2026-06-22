/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.text2speech.Narrator
 *  com.mojang.text2speech.NarratorLinux
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.disablenarrator;

import com.mojang.text2speech.Narrator;
import com.mojang.text2speech.NarratorLinux;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={NarratorLinux.class})
public abstract class NoLinuxNarratorMixin
implements Narrator {
    @Inject(method={"<init>"}, at={@At(value="INVOKE", target="Ljava/lang/Object;<init>()V", shift=At.Shift.AFTER, remap=false)}, cancellable=true)
    private void onInit(CallbackInfo ci) {
        ci.cancel();
    }

    @Overwrite
    public void say(String msg, boolean interrupt, float volume) {
    }

    public void clear() {
    }

    public void destroy() {
    }
}

