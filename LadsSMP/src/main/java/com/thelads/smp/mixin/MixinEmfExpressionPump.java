package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** See {@link MixinEmfModelPartPump}: the animation expression parser runs thousands of times per model. */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.math.expression_tree.MathExpressionParser", remap = false)
public class MixinEmfExpressionPump {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
