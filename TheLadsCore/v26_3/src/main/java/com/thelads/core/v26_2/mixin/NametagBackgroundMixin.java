package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.NativeNicknames;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
/** Nametags: renames, background and text shadow, set on the submitted tag so its readers (Essential's icon padding) follow. */
@Mixin(SubmitNodeCollection.class)
public class NametagBackgroundMixin {
    @ModifyVariable(method = "submitNameTag", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component lads$rename(Component name) {
        return NativeNicknames.rename(name);
    }

    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"), index = 6, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("Nametags") && !NativeQualityOfLife.bool("Nametags", "Render Background", true) ? 0 : vanillaColor;
    }

    /** Shadow on the normal pass only; the see-through pass behind walls stays flat and readable. */
    @ModifyArg(method = "nameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"), index = 3, require = 1)
    private static boolean lads$shadow(boolean shadow, @Local(argsOnly = true) Font.DisplayMode mode) {
        return shadow || mode == Font.DisplayMode.NORMAL && NativeQualityOfLife.enabled("Nametags") && NativeQualityOfLife.bool("Nametags", "Text Shadow", true);
    }
}
