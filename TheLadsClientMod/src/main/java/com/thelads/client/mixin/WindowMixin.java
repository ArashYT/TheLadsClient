package com.thelads.client.mixin;

import com.thelads.client.config.ConfigManager;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin {
    @Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true)
    private void onCalculateScale(CallbackInfoReturnable<Integer> cir) {
        // Only override if the mod's scaling feature is actually enabled, and the user hasn't explicitly disabled it
        if (ConfigManager.getConfig().isUiScalingEnabled()) {
            cir.setReturnValue(1);
        }
    }
}
