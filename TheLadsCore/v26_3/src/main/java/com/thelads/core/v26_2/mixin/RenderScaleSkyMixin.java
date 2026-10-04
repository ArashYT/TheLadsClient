package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * SkyRenderer keeps the main target it was created with (the first world frame, or the last resource reload). While Better
 * Resolution renders the world into its own target, the sky must go there too: otherwise the sky disc, sun, moon and stars land
 * on the native target under the composite, and under Iris they are drawn at the native viewport into the scaled target.
 */
@Mixin(SkyRenderer.class)
public abstract class RenderScaleSkyMixin {
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
        target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"), require = 1)
    private RenderTarget lads$currentWorldTarget(RenderTarget created) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
