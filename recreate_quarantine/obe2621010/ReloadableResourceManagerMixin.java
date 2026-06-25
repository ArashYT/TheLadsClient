package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.resources.ReloadableResourceManager.class)
public class ReloadableResourceManagerMixin {

    @Inject(method = "reload", at = @At("HEAD"), require = 0)
    private void reload(CallbackInfo ci) {
        // Custom logic here
    }
}
