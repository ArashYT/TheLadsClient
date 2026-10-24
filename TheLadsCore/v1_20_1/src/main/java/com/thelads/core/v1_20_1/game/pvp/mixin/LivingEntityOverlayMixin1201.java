package com.thelads.core.v1_20_1.game.pvp.mixin;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.modules.HitColorModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityOverlayMixin1201 {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void lads$customOverlayCoords(LivingEntity entity, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        HitColorModule hit = PvpVisuals.HIT_COLOR;
        if (hit.on && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            HitColorModule.DamageType type = HitColorModule.getDamageType(entity.getId());
            if (type == HitColorModule.DamageType.DEFAULT) {
                if (entity.isOnFire()) type = HitColorModule.DamageType.FIRE;
                else if (entity.isUnderWater() && entity.getAirSupply() <= 0) type = HitColorModule.DamageType.DROWNING;
                else if (entity.fallDistance > 2.0F) type = HitColorModule.DamageType.FALL;
            }
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), type.overlayV));
        }
    }
}
