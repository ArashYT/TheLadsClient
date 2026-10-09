package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.game.RenderScale1201;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class RenderScaleOutlineMixin {
    // Entity outlines are drawn onto the target the world renders into, which Better Resolution makes smaller than the window.
    @ModifyArg(method = "doEntityOutline", index = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V"))
    private int lads$outlineWidth(int width) { return RenderScale1201.outlineSize(width, true); }

    @ModifyArg(method = "doEntityOutline", index = 1, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V"))
    private int lads$outlineHeight(int height) { return RenderScale1201.outlineSize(height, false); }
}
