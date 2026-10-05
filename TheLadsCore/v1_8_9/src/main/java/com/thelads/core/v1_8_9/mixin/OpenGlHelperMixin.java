package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.HudCache189;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

/** HudCache189: while the HUD FPS cap's cache is drawn, straight-alpha blending accumulates premultiplied alpha (GlStateManager's tryBlendFuncSeparate ends here). */
@Mixin(OpenGlHelper.class)
public abstract class OpenGlHelperMixin {
    @Inject(method = "glBlendFunc", at = @At("HEAD"), cancellable = true)
    private static void ladsPremultiplied(int src, int dst, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (!HudCache189.premultiplied(src, dst) || srcAlpha == GL11.GL_ONE && dstAlpha == GL11.GL_ONE_MINUS_SRC_ALPHA) return;
        ci.cancel();
        OpenGlHelper.glBlendFunc(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
