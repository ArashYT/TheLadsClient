package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.modules.FogCustomizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fog Customizer on 26.3: setupFog's FogData (an environment's fog plus the render-distance fog), right after vanilla's last write.
 * Not at the return: Sodium copies the FogData for its terrain there, and a change after that copy reached only the entities and
 * sky. A cleared medium's environmental fog goes past anything drawn (Disable Fog: the render-distance fog too); the atmospheric and
 * render-distance fog's start and end scale. The sky and cloud ends stay vanilla's.
 */
@Mixin(FogRenderer.class)
public abstract class FogCustomizerMixin {
    @Inject(method = "setupFog", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
        opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void lads$customFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
            ClientLevel level, CallbackInfoReturnable<FogData> cir, @Local FogData data) {
        FogCustomizerModule fog = FogCustomizerModule.get();
        if (fog == null || !fog.isEnabled()) return;
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
    }
}
