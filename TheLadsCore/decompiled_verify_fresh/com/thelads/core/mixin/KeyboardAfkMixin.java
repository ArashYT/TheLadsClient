/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.KeyboardHandler
 *  net.minecraft.client.input.KeyEvent
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DynamicFPSModule;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={KeyboardHandler.class})
public class KeyboardAfkMixin {
    @Inject(method={"keyPress"}, at={@At(value="HEAD")}, require=0)
    private void ladsKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("DynamicFPS");
        if (m instanceof DynamicFPSModule) {
            DynamicFPSModule dynFps = (DynamicFPSModule)m;
            dynFps.onInput();
        }
    }
}

