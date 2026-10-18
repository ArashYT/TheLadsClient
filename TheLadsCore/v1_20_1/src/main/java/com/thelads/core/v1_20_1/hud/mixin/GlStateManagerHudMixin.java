package com.thelads.core.v1_20_1.hud.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.GlStateManager;
import com.thelads.core.v1_20_1.hud.HudCache1201;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * HudCache1201: while the HUD FPS cap's cache is drawn, straight-alpha blending (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) accumulates
 * premultiplied alpha (ONE, ONE_MINUS_SRC_ALPHA) in the cache, so its one composite matches drawing on the screen. GlStateManager's
 * cache sees the changed factors, so it stays in step with GL.
 */
@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlStateManagerHudMixin {
    @ModifyVariable(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int lads$srcAlpha(int srcAlpha, @Local(ordinal = 0, argsOnly = true) int srcRgb, @Local(ordinal = 1, argsOnly = true) int dstRgb) {
        return HudCache1201.premultiplied(srcRgb, dstRgb) ? GL11.GL_ONE : srcAlpha;
    }

    @ModifyVariable(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static int lads$dstAlpha(int dstAlpha, @Local(ordinal = 0, argsOnly = true) int srcRgb, @Local(ordinal = 1, argsOnly = true) int dstRgb) {
        return HudCache1201.premultiplied(srcRgb, dstRgb) ? GL11.GL_ONE_MINUS_SRC_ALPHA : dstAlpha;
    }

    @Redirect(method = "_blendFunc(II)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V"))
    private static void lads$blendFunc(int src, int dst) {
        if (HudCache1201.premultiplied(src, dst)) GL14.glBlendFuncSeparate(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        else GL11.glBlendFunc(src, dst);
    }
}
