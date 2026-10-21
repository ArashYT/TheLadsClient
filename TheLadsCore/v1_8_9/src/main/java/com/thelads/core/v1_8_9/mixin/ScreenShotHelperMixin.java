package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Screenshots189;
import java.io.File;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BetterScreenshots (Screenshots189): In 1.8.9, saveScreenshot reads the framebuffer and converts/encodes/writes PNG
 * on the render thread. Keep glReadPixels on render thread; offload conversion, PNG encoding, and file write to daemon IO.
 */
@Mixin(ScreenShotHelper.class)
public abstract class ScreenShotHelperMixin {
    @Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
        at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsAsyncScreenshot(File gameDirectory, String screenshotName, int width, int height, Framebuffer buffer, CallbackInfoReturnable<IChatComponent> cir) {
        Screenshots189.saveScreenshotAsync(gameDirectory, screenshotName, width, height, buffer);
        cir.setReturnValue(null);
    }
}
