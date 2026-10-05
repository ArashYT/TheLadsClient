package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_8_9.feature.Crosshair189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.Probe172Anim;
import com.thelads.core.v1_8_9.feature.Zoom189;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crosshair Tweaks: F3's 3D crosshair (the world axes) follows Keep Vanilla Debug, Disable Crosshair and the visibility rules,
 * as 26.x NativeCrosshair.suppressDebugAxes. OptiFine patches EntityRenderer, so the hook is optional.
 * 1.7 Animations, Instant sneak camera: orientCamera's one eye-height read (one in OptiFine M5's orientCamera too) becomes 1.7's.
 * Zoom: getFOVModifier (kept by OptiFine M5) says whether the FOV Forge's FOVModifier event is about to get is the held item's.
 * Damage tilt: hurtCameraEffect's direction and 14° tilt, as 26.x DamageTiltMixin (common DamageTilt).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "renderWorldDirections", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsDebugAxes(float partialTicks, CallbackInfo ci) {
        if (Crosshair189.suppressDebugAxes()) ci.cancel();
    }

    @Inject(method = "getFOVModifier", at = @At("HEAD"), require = 1)
    private void ladsZoomPass(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        Zoom189.handPass = !useFOVSetting;
    }

    @Redirect(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getEyeHeight()F"), require = 1, allow = 1)
    private float ladsEyeHeight(Entity entity, float partialTicks) {
        return OldAnimations189.cameraEyeHeight(entity, partialTicks);
    }

    /** Damage tilt (the OldDamageTilt module): the hit's direction, which 1.8.9 leaves at 0 for its own player, or 0 (Directional off). */
    @Redirect(method = "hurtCameraEffect", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;attackedAtYaw:F"),
        require = 1, allow = 1)
    private float ladsTiltDirection(EntityLivingBase entity) {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        return module.isEnabled() ? DamageTilt.CLIENT.cameraYaw(module, System.currentTimeMillis()) : entity.attackedAtYaw;
    }

    /** Damage tilt: Intensity scales the 14° tilt (0 is none); the death roll stays 1.8.9's. */
    @ModifyConstant(method = "hurtCameraEffect", constant = @Constant(floatValue = 14.0F), require = 1, allow = 1)
    private float ladsTiltIntensity(float degrees) {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        return module.isEnabled() ? degrees * DamageTilt.strength(module) : degrees;
    }

    @Inject(method = "hurtCameraEffect", at = @At("TAIL"), require = 1)
    private void ladsQaTilt(float partialTicks, CallbackInfo ci) {
        if (Probe172Anim.recording) Probe172Anim.tiltFrame();
    }
}
