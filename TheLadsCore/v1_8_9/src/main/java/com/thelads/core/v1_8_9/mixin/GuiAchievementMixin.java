package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.qa.QaProgress;
import com.thelads.core.v1_8_9.feature.NativeHud;
import net.minecraft.client.gui.achievement.GuiAchievement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * QA only (1.9.13 HUDFIX): draws the QA progress bar after any tutorial/achievement toast finishes drawing.
 * In 1.8.9, Minecraft.runGameLoop calls updateAchievementWindow after EntityRenderer.updateCameraAndRender,
 * so drawing here ensures the QA bar is never obscured by the achievement window toast.
 */
@Mixin(GuiAchievement.class)
public abstract class GuiAchievementMixin {
    @Inject(method = "updateAchievementWindow", at = @At("RETURN"))
    private void ladsDrawQaBarAfterAchievement(CallbackInfo ci) {
        if (QaProgress.on()) {
            NativeHud.drawQaBar();
        }
    }
}
