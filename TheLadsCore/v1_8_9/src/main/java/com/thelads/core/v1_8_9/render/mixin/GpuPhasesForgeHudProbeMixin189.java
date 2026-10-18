package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.GpuPhases189;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** QA (-Dlads.gpuPhases=1): the CPU time of Forge's HUD parts and of its overlay events (GpuPhases189). */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GpuPhasesForgeHudProbeMixin189 {
    private static void begin(int phase) { if (GpuPhases189.ON) GpuPhases189.begin(phase); }
    private static void end(int phase) { if (GpuPhases189.ON) GpuPhases189.end(phase); }

    @Inject(method = "renderCrosshairs", at = @At("HEAD")) private void lads$a0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.CROSSHAIR); }
    @Inject(method = "renderCrosshairs", at = @At("RETURN")) private void lads$a1(int w, int h, CallbackInfo ci) { end(GpuPhases189.CROSSHAIR); }
    @Inject(method = "renderTooltip", at = @At("HEAD"), remap = true, require = 0) private void lads$b0(CallbackInfo ci) { begin(GpuPhases189.HOTBAR); }
    @Inject(method = "renderTooltip", at = @At("RETURN"), remap = true, require = 0) private void lads$b1(CallbackInfo ci) { end(GpuPhases189.HOTBAR); }
    @Inject(method = "renderHealth", at = @At("HEAD")) private void lads$c0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.BARS); }
    @Inject(method = "renderHealth", at = @At("RETURN")) private void lads$c1(int w, int h, CallbackInfo ci) { end(GpuPhases189.BARS); }
    @Inject(method = "renderFood", at = @At("HEAD")) private void lads$d0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.BARS); }
    @Inject(method = "renderFood", at = @At("RETURN")) private void lads$d1(int w, int h, CallbackInfo ci) { end(GpuPhases189.BARS); }
    @Inject(method = "renderArmor", at = @At("HEAD")) private void lads$e0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.BARS); }
    @Inject(method = "renderArmor", at = @At("RETURN")) private void lads$e1(int w, int h, CallbackInfo ci) { end(GpuPhases189.BARS); }
    @Inject(method = "renderAir", at = @At("HEAD")) private void lads$f0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.BARS); }
    @Inject(method = "renderAir", at = @At("RETURN")) private void lads$f1(int w, int h, CallbackInfo ci) { end(GpuPhases189.BARS); }
    @Inject(method = "renderExperience", at = @At("HEAD")) private void lads$g0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.BARS); }
    @Inject(method = "renderExperience", at = @At("RETURN")) private void lads$g1(int w, int h, CallbackInfo ci) { end(GpuPhases189.BARS); }
    @Inject(method = "renderChat", at = @At("HEAD")) private void lads$h0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.CHAT); }
    @Inject(method = "renderChat", at = @At("RETURN")) private void lads$h1(int w, int h, CallbackInfo ci) { end(GpuPhases189.CHAT); }
    @Inject(method = "renderHUDText", at = @At("HEAD")) private void lads$i0(int w, int h, CallbackInfo ci) { begin(GpuPhases189.HUD_TEXT); }
    @Inject(method = "renderHUDText", at = @At("RETURN")) private void lads$i1(int w, int h, CallbackInfo ci) { end(GpuPhases189.HUD_TEXT); }
    @Inject(method = "pre", at = @At("HEAD")) private void lads$j0(CallbackInfoReturnable<Boolean> ci) { begin(GpuPhases189.EVENTS); }
    @Inject(method = "pre", at = @At("RETURN")) private void lads$j1(CallbackInfoReturnable<Boolean> ci) { end(GpuPhases189.EVENTS); }
    @Inject(method = "post", at = @At("HEAD")) private void lads$k0(CallbackInfo ci) { begin(GpuPhases189.EVENTS); }
    @Inject(method = "post", at = @At("RETURN")) private void lads$k1(CallbackInfo ci) { end(GpuPhases189.EVENTS); }
}
