package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Damage tilt (the OldDamageTilt module, common DamageTilt), as 1.8.9's EntityRendererMixin: vanilla's timing and death roll stay. The
 * tilt turns by the hit's direction (Directional; DamageTiltPacketsMixin pairs it with the hurt, so fall damage gets the fixed tilt
 * instead of vanilla's stale hurtDir) or the old fixed way, and Intensity scales Minecraft's own Damage Tilt setting.
 */
@Mixin(GameRenderer.class)
public class DamageTiltMixin {
    @Redirect(method = "bobHurt", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;hurtDir:F"), require = 1)
    private float lads$direction(CameraEntityRenderState state) {
        if (!NativeQualityOfLife.enabled(DamageTilt.MODULE)) return state.hurtDir;
        return DamageTilt.CLIENT.cameraYaw(NativeQualityOfLife.module(DamageTilt.MODULE), System.currentTimeMillis());
    }

    @Redirect(method = "bobHurt", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"), require = 1)
    private double lads$intensity(OptionsRenderState state) {
        if (!NativeQualityOfLife.enabled(DamageTilt.MODULE)) return state.damageTiltStrength;
        return state.damageTiltStrength * DamageTilt.CLIENT.cameraStrength(NativeQualityOfLife.module(DamageTilt.MODULE), System.currentTimeMillis());
    }
}
