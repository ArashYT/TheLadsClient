package com.thelads.core.mixin;

import com.thelads.core.modules.DynamicFPSModule;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Final;

@Mixin(FramerateLimitTracker.class)
public abstract class DynamicFPSMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetFramerateLimit(CallbackInfoReturnable<Integer> cir) {
        Module baseModule = ModuleManager.getInstance().getModule("DynamicFPS");
        if (baseModule instanceof DynamicFPSModule dynFps) {
            dynFps.setOriginalFramerateLimit(cir.getReturnValue());
            dynFps.onWindowFocusChanged(this.minecraft.isWindowActive());
            cir.setReturnValue(dynFps.getCurrentFramerateLimit());
        }
    }
}
