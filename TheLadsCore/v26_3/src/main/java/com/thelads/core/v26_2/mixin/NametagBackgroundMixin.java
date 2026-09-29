package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Mixin(SubmitNodeCollection.class)
public class NametagBackgroundMixin {
    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"), index = 6, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("ToggleNametags") && !NativeQualityOfLife.bool("ToggleNametags", "Render Background", true) ? 0 : vanillaColor;
    }
}
