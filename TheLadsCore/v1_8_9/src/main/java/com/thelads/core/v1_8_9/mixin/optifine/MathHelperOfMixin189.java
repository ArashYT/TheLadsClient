package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OptiFine Fast Math setting and reflective compatibility:
 * - Injects public static boolean fastMath field onto MathHelper (verified by Probe192OfDefaults)
 * - Keeps sine and cosine synchronized with Fast Math toggle
 */
@Mixin(MathHelper.class)
public abstract class MathHelperOfMixin189 {
    public static boolean fastMath = true;
    @Shadow private static float[] SIN_TABLE;

    @Inject(method = "sin", at = @At("HEAD"), cancellable = true)
    private static void lads$fastSin(float value, CallbackInfoReturnable<Float> cir) {
        if (fastMath && OptiFine189Module.sOfFastMath) {
            cir.setReturnValue(SIN_TABLE[(int) (value * 10430.378f) & 65535]);
        }
    }

    @Inject(method = "cos", at = @At("HEAD"), cancellable = true)
    private static void lads$fastCos(float value, CallbackInfoReturnable<Float> cir) {
        if (fastMath && OptiFine189Module.sOfFastMath) {
            cir.setReturnValue(SIN_TABLE[(int) (value * 10430.378f + 16384.0f) & 65535]);
        }
    }
}
