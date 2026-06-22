package com.thelads.core.mixin.client;

import com.thelads.core.modules.ExordiumModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class ExordiumMixin {
    private long lastFrameTime = 0;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void ladsLimitGuiFps(boolean renderLevel, CallbackInfo ci) {
        // Disabled by user request
    }
}
