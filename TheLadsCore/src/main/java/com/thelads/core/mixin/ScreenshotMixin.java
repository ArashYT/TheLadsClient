package com.thelads.core.mixin;

import com.thelads.core.client.ScreenshotMeta;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * When a screenshot filename is chosen, write a metadata sidecar next to it.
 * require = 0 so a mapping change just skips the sidecar instead of crashing.
 */
@Mixin(Screenshot.class)
public class ScreenshotMixin {
    @Inject(method = "getFile", at = @At("RETURN"), require = 0)
    private static void ladsWriteMeta(File picDir, CallbackInfoReturnable<File> cir) {
        File png = cir.getReturnValue();
        if (png != null) {
            ScreenshotMeta.write(png);
        }
    }
}
