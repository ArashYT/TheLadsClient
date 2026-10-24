package com.thelads.core.v1_8_9.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.ToastControlModule;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.stats.Achievement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toast Control on 1.8.9: its pop-ups are achievements (Advancement) and the "Open your inventory" hint (Tutorial), hidden or moved.
 */
@Mixin(GuiAchievement.class)
public class AchievementMixin189 {
    @Unique private boolean lads$moved;

    @Inject(method = "displayAchievement", at = @At("HEAD"), cancellable = true)
    private void lads$achievement(Achievement achievement, CallbackInfo ci) {
        if (HudExtras.TOASTS.hides(ToastControlModule.ADVANCEMENT)) ci.cancel();
    }

    @Inject(method = "displayUnformattedAchievement", at = @At("HEAD"), cancellable = true)
    private void lads$hint(Achievement achievement, CallbackInfo ci) {
        if (HudExtras.TOASTS.hides(ToastControlModule.TUTORIAL)) ci.cancel();
    }

    // After its own matrix setup (updateAchievementWindowScale loads identity), the pop-up is drawn moved.
    @Inject(method = "updateAchievementWindow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/achievement/GuiAchievement;updateAchievementWindowScale()V", shift = At.Shift.AFTER))
    private void lads$move(CallbackInfo ci) {
        ToastControlModule toasts = HudExtras.TOASTS;
        if (toasts.dx == 0 && toasts.dy == 0) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(toasts.dx, toasts.dy, 0);
        lads$moved = true;
    }

    @Inject(method = "updateAchievementWindow", at = @At("RETURN"))
    private void lads$restore(CallbackInfo ci) {
        if (!lads$moved) return;
        lads$moved = false;
        GlStateManager.popMatrix();
    }
}
