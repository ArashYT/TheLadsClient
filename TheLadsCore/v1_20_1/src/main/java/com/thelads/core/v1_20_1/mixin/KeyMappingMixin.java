package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    /** The toggle checks read keySprint/keyShift.isDown() themselves: those inner calls must see the real key, not recurse (StackOverflowError on the first tick in a world). */
    private static boolean inToggleCheck;

    @Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
    private void onIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (inToggleCheck) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        inToggleCheck = true;
        try {
            if ((Object) this == mc.options.keySprint) {
                if (NativeFeatures1201.isSprintToggled()) {
                    cir.setReturnValue(true);
                }
            } else if ((Object) this == mc.options.keyShift) {
                if (NativeFeatures1201.isSneakToggled()) {
                    cir.setReturnValue(true);
                }
            }
        } finally {
            inToggleCheck = false;
        }
    }
}
