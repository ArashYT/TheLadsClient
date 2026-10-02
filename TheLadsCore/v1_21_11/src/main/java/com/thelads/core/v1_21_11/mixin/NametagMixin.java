package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeNicknames;
import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Nametags, as 26.x NametagBackgroundMixin: renames and background, set on the stored tag so its readers (Essential's icon padding) follow. */
@Mixin(NameTagFeatureRenderer.Storage.class)
public class NametagMixin {
    @ModifyVariable(method = "add", at = @At("HEAD"), argsOnly = true, require = 1)
    private Component lads$rename(Component name) {
        return NativeNicknames.rename(name);
    }

    @ModifyArg(method = "add", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V"), index = 6, require = 1)
    private int lads$background(int vanillaColor) {
        return NativeQualityOfLife.enabled("Nametags") && !NativeQualityOfLife.bool("Nametags", "Render Background", true) ? 0 : vanillaColor;
    }
}
