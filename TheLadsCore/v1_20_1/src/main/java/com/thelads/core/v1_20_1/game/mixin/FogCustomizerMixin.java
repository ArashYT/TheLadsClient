package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.modules.FogCustomizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fog Customizer on 1.20.1: after setupFog set the shader fog, a cleared fog goes past anything drawn and the terrain fog's start and
 * end scale (the sky fog keeps vanilla's distances unless Disable Fog clears it).
 */
@Mixin(FogRenderer.class)
public abstract class FogCustomizerMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void lads$customFog(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick,
            CallbackInfo ci) {
        FogCustomizerModule fog = FogCustomizerModule.get();
        if (fog == null || !fog.isEnabled()) return;
        FogType type = camera.getFluidInCamera();
        Entity entity = camera.getEntity();
        boolean effect = entity instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
        int medium = FogCustomizerModule.medium(type == FogType.LAVA || type == FogType.POWDER_SNOW, effect, type == FogType.WATER);
        if (fog.clears(medium)) {
            RenderSystem.setShaderFogStart(FogCustomizerModule.FAR);
            RenderSystem.setShaderFogEnd(FogCustomizerModule.FAR);
        } else if (medium == FogCustomizerModule.TERRAIN && mode == FogRenderer.FogMode.FOG_TERRAIN) {
            float start = fog.start(RenderSystem.getShaderFogStart());
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(fog.end(RenderSystem.getShaderFogEnd(), start));
        }
    }
}
