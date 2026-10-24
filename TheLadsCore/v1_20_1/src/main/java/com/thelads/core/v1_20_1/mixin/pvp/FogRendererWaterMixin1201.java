package com.thelads.core.v1_20_1.mixin.pvp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: the water fog reaches 1 / Water Fog as far (at most the render distance). Blindness and Darkness keep their fog. */
@Mixin(FogRenderer.class)
public class FogRendererWaterMixin1201 {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void lads$clearWater(Camera camera, FogRenderer.FogMode mode, float distance, boolean thick, float partialTick, CallbackInfo ci) {
        float factor = PvpVisuals.waterFog;
        if (factor >= 1 || camera.getFluidInCamera() != FogType.WATER) return;
        if (camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        RenderSystem.setShaderFogEnd(Math.min(distance, RenderSystem.getShaderFogEnd() / Math.max(factor, 0.01f)));
    }
}
