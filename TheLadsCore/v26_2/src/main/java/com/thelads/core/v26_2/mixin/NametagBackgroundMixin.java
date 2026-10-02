package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeNicknames;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nametags: renames and background, set on the submitted tag so its readers (Essential's icon padding) follow. */
@Mixin(SubmitNodeCollection.class)
public class NametagBackgroundMixin {
    @ModifyVariable(method = "submitNameTag", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component lads$rename(Component name) {
        return NativeNicknames.rename(name);
    }

    // Name tags use a dedicated submit in 26.2; chat/tooltips keep their backgrounds.
    @ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIILnet/minecraft/client/gui/Font$DisplayMode;)V"), index = 6, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("Nametags")
            && !NativeQualityOfLife.bool("Nametags", "Render Background", true) ? 0 : vanillaColor;
    }
}
