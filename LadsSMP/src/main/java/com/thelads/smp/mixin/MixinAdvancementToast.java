package com.thelads.smp.mixin;

import com.thelads.smp.config.LadsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consolidates 'AdvancementScreenshot': Automatically captures a screenshot 1 second after an advancement toast becomes visible.
 */
@Mixin(AdvancementToast.class)
public abstract class MixinAdvancementToast {

    @Unique
    private boolean ladssmp$screenshotTaken;

    @Unique
    private static long ladssmp$lastScreenshotTimeMs;

    @Inject(method = "update", at = @At("HEAD"))
    private void ladssmp$onToastUpdate(ToastManager manager, long timeSinceLastVisible, CallbackInfo ci) {
        if (!LadsConfig.get().features.advancementScreenshot) return;
        if (timeSinceLastVisible <= 1000L || this.ladssmp$screenshotTaken) return;
        this.ladssmp$screenshotTaken = true;
        long now = System.currentTimeMillis();
        if (now - ladssmp$lastScreenshotTimeMs >= 1000L) {
            ladssmp$lastScreenshotTimeMs = now;
            Screenshot.grab(Minecraft.getInstance(), false);
        }
    }
}
