package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.CloudHeightModule;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldProvider.class)
public abstract class WorldProviderMixin {
    @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true, require = 0)
    private void ladsCloudHeight(CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValueF();
        cir.setReturnValue(CloudHeightModule.getTargetCloudHeight(vanilla, 256));
    }
}
