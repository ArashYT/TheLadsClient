package com.thelads.core.mixin.renderscale;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.client.renderscale.RenderScaleManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Minecraft.class})
public class MinecraftMixin {
    @Inject(method={"getMainRenderTarget"}, at={@At(value="HEAD")}, cancellable=true)
    private void onGetMainRenderTarget(CallbackInfoReturnable<RenderTarget> cir) {
        if (RenderScaleManager.shouldRedirectTarget()) {
            cir.setReturnValue(RenderScaleManager.getScaledRenderTarget());
        }
    }
}
