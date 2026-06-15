/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MouseHandler
 *  net.minecraft.client.input.MouseButtonInfo
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DynamicFPSModule;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MouseHandler.class})
public class MouseAfkMixin {
    @Inject(method={"onMove"}, at={@At(value="HEAD")}, require=0)
    private void ladsMouseMove(long window, double x, double y, CallbackInfo ci) {
        MouseAfkMixin.touchAfk();
    }

    @Inject(method={"onButton"}, at={@At(value="HEAD")}, require=0)
    private void ladsMousePress(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        MouseAfkMixin.touchAfk();
    }

    private static void touchAfk() {
        Module m = ModuleManager.getInstance().getModule("DynamicFPS");
        if (m instanceof DynamicFPSModule) {
            DynamicFPSModule dynFps = (DynamicFPSModule)m;
            dynFps.onInput();
        }
    }
}

