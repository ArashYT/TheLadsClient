package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.WindowIcon189;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game window and taskbar show The Lads artwork instead of the grass block, in every size the artwork has (WindowIcon189). */
@Mixin(Minecraft.class)
public abstract class WindowIconMixin189 {
    /** Vanilla hands LWJGL its 16 and 32 pixel icons here; the Lads ones replace them (macOS keeps vanilla's). */
    @Inject(method = "setWindowIcon", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsWindowIcons(CallbackInfo ci) {
        if (WindowIcon189.set()) ci.cancel();
    }

    /** The window exists now: the icons go to it again (the LWJGL 3 shim needs that), and LWJGL 2 on Windows, which takes only 16 and 32 pixels, gets the sizes its DPI asks for. */
    @Inject(method = "createDisplay", at = @At("RETURN"), require = 1)
    private void ladsWindowIconsForDpi(CallbackInfo ci) {
        WindowIcon189.afterDisplay();
    }
}
