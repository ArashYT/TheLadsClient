package com.thelads.core.mixin;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = fuzs.resourcepackoverrides.common.config.ResourceOverridesManager.class, remap = false)
public class ResourceOverridesManagerMixin {

    @Inject(method = "getOverride", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetOverride(String packId, CallbackInfoReturnable<fuzs.resourcepackoverrides.common.server.packs.PackSelectionOverride> cir) {
        Option opt = ModuleManager.getInstance().getModule("TexturePacks").getOption("Disable Hidden Overrides");
        if (opt instanceof BoolOption && ((BoolOption) opt).get()) {
            cir.setReturnValue(fuzs.resourcepackoverrides.common.server.packs.PackSelectionOverride.EMPTY);
        }
    }
}
