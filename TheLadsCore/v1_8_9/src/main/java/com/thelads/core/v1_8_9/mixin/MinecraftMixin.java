package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Borderless189;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** F11 and the fullscreen option: borderless with the BorderlessFullscreen module, and a window that stays resizable after fullscreen. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow private boolean fullscreen;

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsBorderless(CallbackInfo ci) {
        if (!Borderless189.active() && (fullscreen || !Borderless189.enabled())) return;
        fullscreen = Borderless189.toggle((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "toggleFullscreen", at = @At("TAIL"), require = 1)
    private void ladsResizable(CallbackInfo ci) {
        if (!fullscreen) Borderless189.resizable();
    }
}
