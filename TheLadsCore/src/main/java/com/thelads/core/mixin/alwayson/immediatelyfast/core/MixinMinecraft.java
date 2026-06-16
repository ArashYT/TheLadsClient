/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.core;

import net.minecraft.client.Minecraft;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Minecraft.class})
public abstract class MixinMinecraft {
    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void initImmediatelyFast(CallbackInfo ci) {
        ImmediatelyFast.lateInit();
    }

    @Inject(method={"setLevel"}, at={@At(value="HEAD")})
    private void hookLevelChange(CallbackInfo ci) {
        ImmediatelyFast.onLevelChange();
    }
}

