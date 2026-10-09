package com.thelads.core.v26_2.render.mixin;

import com.mojang.renderpearl.api.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Lads Kerria (1.8.5): animation frame textures may also be copy sources, so AnimationCopy26 can copy them into the atlas. */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture")
public abstract class AnimatedTextureAnimationMixin {
    @ModifyArg(method = "createAnimationState", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createTexture(Ljava/util/function/Supplier;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;"),
        index = 1)
    private int lads$copySource(int usage) {
        return usage | GpuTexture.USAGE_COPY_SRC;
    }
}
