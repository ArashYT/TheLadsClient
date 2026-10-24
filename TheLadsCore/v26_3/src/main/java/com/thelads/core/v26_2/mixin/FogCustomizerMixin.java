package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.FogCustomizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fog Customizer on 26.3: the FogData setupFog returns (an environment's fog plus the render-distance fog) before it is uploaded. A
 * cleared medium's environmental fog goes past anything drawn (Disable Fog: the render-distance fog too); the atmospheric and
 * render-distance fog's start and end scale. The sky and cloud ends stay vanilla's.
 */
@Mixin(FogRenderer.class)
public abstract class FogCustomizerMixin {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"), require = 1)
    private FogData lads$customFog(FogData data, @Local(argsOnly = true) Camera camera) {
        FogCustomizerModule fog = FogCustomizerModule.get();
        if (fog == null || !fog.isEnabled()) return data;
        FogType type = camera.getFluidInCamera(); // NONE: the atmospheric fog, as FogRenderer.getFogType reads it
        boolean effect = camera.entity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
        int medium = FogCustomizerModule.medium(type == FogType.LAVA || type == FogType.POWDER_SNOW, effect, type == FogType.WATER);
        if (fog.clears(medium)) {
            data.environmentalStart = data.environmentalEnd = FogCustomizerModule.FAR;
            if (medium == FogCustomizerModule.TERRAIN) data.renderDistanceStart = data.renderDistanceEnd = FogCustomizerModule.FAR;
        } else if (medium == FogCustomizerModule.TERRAIN) {
            data.environmentalStart = fog.start(data.environmentalStart);
            data.environmentalEnd = fog.end(data.environmentalEnd, data.environmentalStart);
            data.renderDistanceStart = fog.start(data.renderDistanceStart);
            data.renderDistanceEnd = fog.end(data.renderDistanceEnd, data.renderDistanceStart);
        }
        return data;
    }
}
