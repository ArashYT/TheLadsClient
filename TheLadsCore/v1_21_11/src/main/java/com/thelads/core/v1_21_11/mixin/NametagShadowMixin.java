package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Nametags Text Shadow on the normal pass (second draw loop) only; the see-through pass behind walls stays flat and readable. */
@Mixin(NameTagFeatureRenderer.class)
public class NametagShadowMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V", ordinal = 1), index = 4, require = 1)
    private boolean lads$shadow(boolean shadow) {
        return shadow || NativeQualityOfLife.enabled("Nametags") && NativeQualityOfLife.bool("Nametags", "Text Shadow", true);
    }
}
