package com.thelads.core.mixin;

import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsOnScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        ZoomModule zoom = ZoomModule.getInstance();
        if (zoom != null && zoom.isActive()) {
            zoom.onScroll(vertical);
            ci.cancel();
        }
    }
}
