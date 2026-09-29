package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.crosshair.NativeCrosshair;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugCrosshairRenderer.class)
public class CrosshairDebugMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true,require=1)
    private void lads$debugVisibility(CallbackInfo callback){if(NativeCrosshair.suppressDebugAxes())callback.cancel();}
}
