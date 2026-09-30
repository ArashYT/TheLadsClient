package com.thelads.core.v1_21_11.mixin.hud;

import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Lads BossBar widget replaces vanilla's bars and owns their world effects (26.x BossBarMixin). */
@Mixin(BossHealthOverlay.class)
public class BossBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ladsCustomBars(GuiGraphics g, CallbackInfo ci) { if (NativeQualityOfLife.enabled("BossBar")) ci.cancel(); }
    @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
    private void ladsSky(CallbackInfoReturnable<Boolean> ci) { if (NativeQualityOfLife.enabled("BossBar") && !NativeQualityOfLife.bool("BossBar", "Darken sky", true)) ci.setReturnValue(false); }
    @Inject(method = "shouldCreateWorldFog", at = @At("HEAD"), cancellable = true)
    private void ladsFog(CallbackInfoReturnable<Boolean> ci) { if (NativeQualityOfLife.enabled("BossBar") && !NativeQualityOfLife.bool("BossBar", "Boss fog", true)) ci.setReturnValue(false); }
    @Inject(method = "shouldPlayMusic", at = @At("HEAD"), cancellable = true)
    private void ladsMusic(CallbackInfoReturnable<Boolean> ci) { if (NativeQualityOfLife.enabled("BossBar") && !NativeQualityOfLife.bool("BossBar", "Boss music", true)) ci.setReturnValue(false); }
}
