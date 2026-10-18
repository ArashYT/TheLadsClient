package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.GpuPhases189;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (-Dlads.gpuPhases=1): the world pass with its sky, terrain, entities, clouds and hand, and the HUD (GpuPhases189). */
@Mixin(EntityRenderer.class)
public abstract class GpuPhasesWorldProbeMixin189 {
    private static final String SKY = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V";
    private static final String LAYER = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I";
    private static final String ENTITIES = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V";
    private static final String HUD = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V";

    @Inject(method = "renderWorldPass", at = @At("HEAD"))
    private void lads$worldStart(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.WORLD); }

    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void lads$worldEnd(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.WORLD); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = SKY), require = 0)
    private void lads$skyStart(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.SKY); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = SKY, shift = At.Shift.AFTER), require = 0)
    private void lads$skyEnd(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.SKY); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = LAYER), require = 0)
    private void lads$terrainStart(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.TERRAIN); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = LAYER, shift = At.Shift.AFTER), require = 0)
    private void lads$terrainEnd(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.TERRAIN); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = ENTITIES), require = 0)
    private void lads$entitiesStart(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.ENTITIES); }

    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE", target = ENTITIES, shift = At.Shift.AFTER), require = 0)
    private void lads$entitiesEnd(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.ENTITIES); }

    @Inject(method = "renderCloudsCheck", at = @At("HEAD"), require = 0)
    private void lads$cloudsStart(CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.CLOUDS); }

    @Inject(method = "renderCloudsCheck", at = @At("RETURN"), require = 0)
    private void lads$cloudsEnd(CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.CLOUDS); }

    @Inject(method = "renderHand", at = @At("HEAD"), require = 0)
    private void lads$handStart(float partialTicks, int xOffset, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.HAND); }

    @Inject(method = "renderHand", at = @At("RETURN"), require = 0)
    private void lads$handEnd(float partialTicks, int xOffset, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.HAND); }

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = HUD), require = 0)
    private void lads$hudStart(float partialTicks, long nanoTime, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.HUD); }

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = HUD, shift = At.Shift.AFTER), require = 0)
    private void lads$hudEnd(float partialTicks, long nanoTime, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.HUD); }
}
