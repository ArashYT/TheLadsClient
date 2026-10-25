package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.HitColorModule;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityOverlayMixin26 {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void lads$customOverlayCoords(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        if (hit.on && state.hasRedOverlay) {
            int v = ((PvpVisuals26.DamageState) state).lads$damageV();
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), v));
        }
    }
}
