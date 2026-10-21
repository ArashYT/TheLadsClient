package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.modules.CloudHeightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DimensionSpecialEffects.class)
public abstract class DimensionSpecialEffectsMixin {
    @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true, require = 0)
    private void ladsCloudHeight(CallbackInfoReturnable<Float> cir) {
        float vanilla = cir.getReturnValueF();
        if (Float.isNaN(vanilla)) return;
        int maxHeight = 320;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            maxHeight = mc.level.getMaxBuildHeight();
        }
        cir.setReturnValue(CloudHeightModule.getTargetCloudHeight(vanilla, maxHeight));
    }
}
