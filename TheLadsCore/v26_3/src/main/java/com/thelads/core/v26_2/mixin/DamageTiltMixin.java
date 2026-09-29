package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep vanilla timing/death animations; restore the pre-directional damage angle. */
@Mixin(GameRenderer.class)
public class DamageTiltMixin {
    @Redirect(method = "bobHurt", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/state/level/CameraEntityRenderState;hurtDir:F"), require = 1)
    private float lads$classicAngle(CameraEntityRenderState state) {
        return NativeQualityOfLife.enabled("OldDamageTilt") ? 0 : state.hurtDir;
    }

    @Redirect(method = "bobHurt", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"), require = 1)
    private double lads$intensity(OptionsRenderState state) {
        if (!NativeQualityOfLife.enabled("OldDamageTilt")) return state.damageTiltStrength;
        return state.damageTiltStrength * switch (NativeQualityOfLife.choice("OldDamageTilt", "Intensity", 1)) {
            case 0 -> .5;
            case 2 -> 1.5;
            default -> 1;
        };
    }
}
