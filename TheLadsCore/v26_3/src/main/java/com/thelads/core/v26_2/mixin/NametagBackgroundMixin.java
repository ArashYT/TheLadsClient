package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NametagBackgroundMixin {
    // Name tags use a dedicated renderer in 26.2; chat/tooltips keep their backgrounds.
    @ModifyArg(method = "prepareText", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"), index = 6, require = 1)
    private static int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("ToggleNametags")
            && !NativeQualityOfLife.bool("ToggleNametags", "Render Background", true) ? 0 : vanillaColor;
    }
}
