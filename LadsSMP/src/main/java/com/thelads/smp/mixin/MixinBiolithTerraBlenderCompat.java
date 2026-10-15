package com.thelads.smp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.api.SurfaceRuleManager;

@Pseudo
@Mixin(targets = "com.terraformersmc.biolith.impl.compat.TerraBlenderCompatFabric", remap = false)
public class MixinBiolithTerraBlenderCompat {

    @Inject(method = "getBootstrapperAsBuilder", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lads$adaptBiolithBootstrapper(
            @Coerce Object bootstrapper,
            CallbackInfoReturnable<SurfaceRuleManager.RuleBuilder> cir) {
        cir.setReturnValue(SurfaceRuleManager.wrapBootstrapper(bootstrapper));
    }
}
