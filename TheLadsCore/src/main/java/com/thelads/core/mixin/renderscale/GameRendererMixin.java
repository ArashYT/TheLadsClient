package com.thelads.core.mixin.renderscale;

import com.thelads.core.client.renderscale.RenderScaleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class})
public class GameRendererMixin {
    @Inject(method={"renderLevel"}, at={@At(value="HEAD")})
    private void onBeginRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderScaleManager.beginRedirect();
    }

    @Inject(method={"renderLevel"}, at={@At(value="RETURN")})
    private void onEndRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderScaleManager.endRedirect();
    }
}
