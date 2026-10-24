package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: the water fog reaches 1 / Water Fog as far (at most the render distance). Blindness and Darkness come first and keep theirs. */
@Mixin(WaterFogEnvironment.class)
public class WaterFogMixin26 {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void lads$clearWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        float factor = PvpVisuals.waterFog;
        if (factor >= 1) return;
        fog.environmentalEnd = Math.min(renderDistance, fog.environmentalEnd / Math.max(factor, 0.01f));
        fog.skyEnd = fog.environmentalEnd;
        fog.cloudEnd = fog.environmentalEnd;
    }
}
