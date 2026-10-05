package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.HudCache189;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.lwjgl.opengl.GL11;

/**
 * HudCache189: blendFunc's GL call, as OpenGlHelperMixin does for tryBlendFuncSeparate (OptiFine's HD fonts and items use this
 * one). OptiFine replaces GlStateManager and keeps this call.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Redirect(method = "blendFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false))
    private static void ladsPremultiplied(int src, int dst) {
        if (HudCache189.premultiplied(src, dst)) OpenGlHelper.glBlendFunc(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        else GL11.glBlendFunc(src, dst);
    }
}
