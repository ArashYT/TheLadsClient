// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.mixin.minecraft.client.gui.components;

import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layers;
import com.thelads.core.v26_2.feature.raised.util.Translate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubtitleOverlay.class, priority = -999999999)
public abstract class SubtitleOverlayMixin {

    /**
     * Moves the {@code subtitles} for {@link Layer} key "minecraft:subtitles".
     */
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;"))
    private void startSubtitlesTranslate(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        Translate.start(guiGraphics.pose(), Layers.SUBTITLES);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;popMatrix()Lorg/joml/Matrix3x2fStack;", shift = At.Shift.AFTER))
    private void endSubtitlesTranslate(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        Translate.end(guiGraphics.pose(), Layers.SUBTITLES);
    }

}