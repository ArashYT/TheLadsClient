// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.mixin.minecraft.client.gui.components;

import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layers;
import com.thelads.core.v26_2.feature.raised.util.Translate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BossHealthOverlay.class, priority = -999999999)
public abstract class BossHealthOverlayMixin {

    /**
     * Moves the {@code bossbar} for {@link Layer} key "minecraft:boss_bar".
     */
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V"))
    private void startBossBarTranslate(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        Translate.start(guiGraphics.pose(), Layers.BOSS_BAR);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V", shift = At.Shift.AFTER))
    private void endBossBarTranslate(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        Translate.end(guiGraphics.pose(), Layers.BOSS_BAR);
    }

}