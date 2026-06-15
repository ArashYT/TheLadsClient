/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.FramerateLimitTracker
 *  net.minecraft.client.Minecraft
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DynamicFPSModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={FramerateLimitTracker.class})
public abstract class DynamicFPSMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method={"getFramerateLimit"}, at={@At(value="RETURN")}, cancellable=true, require=0)
    private void onGetFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        Module baseModule = ModuleManager.getInstance().getModule("DynamicFPS");
        if (baseModule instanceof DynamicFPSModule) {
            DynamicFPSModule dynFps = (DynamicFPSModule)baseModule;
            dynFps.setOriginalFramerateLimit((Integer)cir.getReturnValue());
            dynFps.onWindowFocusChanged(this.minecraft.isWindowActive());
            int limit = dynFps.getCurrentFramerateLimit();
            cir.setReturnValue((Object)limit);
        }
    }
}

