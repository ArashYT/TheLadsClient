package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity Model Features builds every custom entity model while the resources load, one 10 s task; its parts and animation expressions are pump points. */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartCustom", remap = false)
public class MixinEmfModelPartPump {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.poll();
    }
}
