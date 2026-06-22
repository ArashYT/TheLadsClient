package com.thelads.core.mixin.renderscale;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.renderscale.RenderScaleManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={RenderTarget.class})
public class RenderTargetMixin {
    @Inject(method={"resize"}, at={@At(value="HEAD")})
    private void onResize(int width, int height, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && (Object)this == (Object)client.gameRenderer.mainRenderTarget()) {
            RenderScaleManager.onMainTargetResized(width, height);
        }
    }
}
