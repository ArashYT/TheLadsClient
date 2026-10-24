package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: shown while the module is on even with F3+B off (PvpVisuals26.moreHitboxes). */
@Mixin(DebugRenderer.class)
public class PvpDebugRendererMixin26 {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void lads$hitboxes(Frustum frustum, double camX, double camY, double camZ, float partial, CallbackInfo ci) {
        PvpVisuals26.moreHitboxes(camX, camY, camZ, frustum, partial);
    }
}
