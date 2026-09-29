package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeConnectionStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class SignalLossHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"), require = 1)
    private void lads$connectionStatus(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        NativeConnectionStatus.render(graphics);
    }
}
