package com.thelads.core.v1_20_1.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ladsCustomBars(GuiGraphics g, CallbackInfo ci) {
        // The Lads Boss Bar replaces vanilla's; under the tab list or the debug text vanilla's steps aside (HudCover).
        if (NativeFeatures1201.enabled("BossBar") || com.thelads.core.client.hud.HudCover.bossBarsCovered(g.guiWidth(), com.thelads.core.client.bridge.LadsGameBridge.get().bossBarCount())) {
            ci.cancel();
        }
    }

    /** While the Lads API notification is up the bars start below it (slides with it). */
    @WrapMethod(method = "render")
    private void ladsBelowToast(GuiGraphics g, Operation<Void> original) {
        int shift = com.thelads.core.client.hud.ServerRulesNotice.clearOf(g.guiWidth() / 2 - 91, g.guiWidth() / 2 + 91, 12) - 12;
        if (shift <= 0) { original.call(g); return; }
        g.pose().pushPose();
        g.pose().translate(0, shift, 0);
        try { original.call(g); } finally { g.pose().popPose(); }
    }
}
