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
        int limit = cir.getReturnValue();
        Module baseModule = ModuleManager.getInstance().getModule("DynamicFPS");
        if (baseModule instanceof DynamicFPSModule dynFps) {
            dynFps.setOriginalFramerateLimit(limit);
            dynFps.onWindowFocusChanged(this.minecraft.isWindowActive());
            limit = dynFps.getCurrentFramerateLimit();
        }

        int exordiumLimit = com.thelads.core.modules.ExordiumModule.getFpsLimit();
        if (exordiumLimit > 0) {
            if (this.minecraft.isWindowActive()) {
                limit = exordiumLimit;
            } else {
                limit = Math.min(limit, exordiumLimit);
            }
        }
        cir.setReturnValue(limit);
    }
}
