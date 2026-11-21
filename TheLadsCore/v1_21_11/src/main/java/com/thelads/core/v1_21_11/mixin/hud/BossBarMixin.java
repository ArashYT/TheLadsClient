package com.thelads.core.v1_21_11.mixin.hud;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

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
    /** While the Lads API notification is up the bars start below it (slides with it). */
    @WrapMethod(method = "render")
    private void ladsBelowToast(GuiGraphics g, Operation<Void> original) {
        int shift = com.thelads.core.client.hud.ServerRulesNotice.clearOf(g.guiWidth() / 2 - 91, g.guiWidth() / 2 + 91, 12) - 12;
        if (shift <= 0) { original.call(g); return; }
        g.pose().pushMatrix();
        g.pose().translate(0, shift);
        try { original.call(g); } finally { g.pose().popMatrix(); }
    }
}
