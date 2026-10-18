package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_8_9.feature.Crosshair189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.Probe172Anim;
import com.thelads.core.v1_8_9.feature.ResolutionCache189;
import com.thelads.core.v1_8_9.feature.Zoom189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 * Fullbright: forces the lightmap colors to full daylight across every block face.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow private int[] lightmapColors;
    @Shadow private DynamicTexture lightmapTexture;

    @Shadow private boolean lightmapUpdateNeeded;
    private boolean ladsLightmapRecomputed;

    @Inject(method = "updateLightmap", at = @At("HEAD"), require = 1)
    private void ladsLightmapStart(float partialTicks, CallbackInfo ci) {
        ladsLightmapRecomputed = this.lightmapUpdateNeeded;
    }

    /**
     * The lift is applied to the colors the game has just computed, once per computation. The method returns after every call, also
     * when the game left the colors as they were (nothing marked them out of date): lifting them again then compounded, every frame,
     * until any Brightness above 0 was white (1.9.3 on) and a paused menu kept brightening the world behind it.
     */
    @Inject(method = "updateLightmap", at = @At("RETURN"), require = 1)
    private void ladsUpdateLightmap(float partialTicks, CallbackInfo ci) {
        if (ladsLightmapRecomputed && com.thelads.core.v1_8_9.feature.Fullbright189.applyLightmap(this.lightmapColors)) {
            this.lightmapTexture.updateDynamicTexture();
        }
    }

    /** SurvivalTweaks Dimension brightness: the lightmap's gamma scaled, as 26.x scales its brightness option. OptiFine patches this method: optional. */
    @Redirect(method = "updateLightmap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;gammaSetting:F", opcode = org.objectweb.asm.Opcodes.GETFIELD), require = 0)
    private float ladsDimensionBrightness(net.minecraft.client.settings.GameSettings settings) {
        return com.thelads.core.v1_8_9.feature.SurvivalTweaks189.brightness(settings.gammaSetting);
    }

    @Inject(method = "getNightVisionBrightness", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNightVisionBrightness(EntityLivingBase entity, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (com.thelads.core.v1_8_9.feature.Fullbright189.isEnabled() && entity == Minecraft.getMinecraft().thePlayer) {
            float f = com.thelads.core.v1_8_9.feature.Fullbright189.factor();
            if (f > 0) cir.setReturnValue(f);
        }
    }

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

    /** Damage tilt: Intensity scales the 14° tilt (0 is none; held at 0 while the hit's knockback is on its way); the death roll stays 1.8.9's. */
    @ModifyConstant(method = "hurtCameraEffect", constant = @Constant(floatValue = 14.0F), require = 1, allow = 1)
    private float ladsTiltIntensity(float degrees) {
        Module module = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
        return module.isEnabled() ? degrees * DamageTilt.CLIENT.cameraStrength(module, System.currentTimeMillis()) : degrees;
    }

    @Inject(method = "hurtCameraEffect", at = @At("TAIL"), require = 1)
    private void ladsQaTilt(float partialTicks, CallbackInfo ci) {
        if (Probe172Anim.recording) Probe172Anim.tiltFrame();
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "NEW", target = "net/minecraft/client/gui/ScaledResolution"), require = 0)
    private ScaledResolution ladsUpdateCameraResolution(Minecraft mc) {
        return ResolutionCache189.get(mc);
    }

    @Redirect(method = "setupOverlayRendering", at = @At(value = "NEW", target = "net/minecraft/client/gui/ScaledResolution"), require = 0)
    private ScaledResolution ladsSetupOverlayResolution(Minecraft mc) {
        return ResolutionCache189.get(mc);
    }
}
