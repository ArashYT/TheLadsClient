package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Nametags Text Shadow on the normal pass only; the see-through pass behind walls stays flat and readable. */
@Mixin(NameTagFeatureRenderer.class)
public class NametagShadowMixin {
    @ModifyArg(method = "prepareText", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"), index = 4, require = 1)
    private static boolean lads$shadow(boolean shadow, @Local(argsOnly = true) NameTagFeatureRenderer.Submit nameTag) {
        return shadow || nameTag.displayMode() == Font.DisplayMode.NORMAL
            && NativeQualityOfLife.enabled("Nametags") && NativeQualityOfLife.bool("Nametags", "Text Shadow", true);
    }
}
