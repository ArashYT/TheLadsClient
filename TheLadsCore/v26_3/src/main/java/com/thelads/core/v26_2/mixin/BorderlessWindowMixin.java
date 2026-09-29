package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Window.class)
public class BorderlessWindowMixin {
    @Inject(method="useBorderlessFullscreenWindow",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsUseSdlFullscreen(CallbackInfoReturnable<Boolean> ci){ci.setReturnValue(false);}
}
