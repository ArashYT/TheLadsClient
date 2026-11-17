package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.modules.HideCloudsModule;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Clouds: the renderer reads OFF; the saved Clouds option (cloudStatus OptionInstance) is untouched. */
@Mixin(Options.class)
public abstract class HideCloudsOptionsMixin {
    @Inject(method = {"getCloudStatus", "getCloudsType"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void ladsHideClouds(CallbackInfoReturnable<CloudStatus> cir) {
        if (HideCloudsModule.active()) cir.setReturnValue(CloudStatus.OFF);
    }
}
