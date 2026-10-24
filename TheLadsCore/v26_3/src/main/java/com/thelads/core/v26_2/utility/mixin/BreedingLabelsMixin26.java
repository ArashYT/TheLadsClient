package com.thelads.core.v26_2.utility.mixin;

import com.thelads.core.v26_2.utility.Utility26;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Breeding Timer's labels: text gizmos emitted with the debug renderer's, which runs every frame with F3 or without. */
@Mixin(DebugRenderer.class)
abstract class BreedingLabelsMixin26 {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void lads$breedingLabels(Frustum frustum, double camX, double camY, double camZ, float partial, CallbackInfo ci) {
        Utility26.breedingLabels(partial);
    }
}
