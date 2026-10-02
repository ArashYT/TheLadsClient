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
 * BetterScreenshots (Screenshots189): 1.8.9 has no screenshot event. The overload without a file name is the screenshot key's
 * (Minecraft.dispatchKeypresses); named ones (QA captures) show no preview.
 */
@Mixin(ScreenShotHelper.class)
public abstract class ScreenShotHelperMixin {
    @Inject(method = "saveScreenshot(Ljava/io/File;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
        at = @At("RETURN"), require = 1)
    private static void ladsSaved(File gameDirectory, int width, int height, Framebuffer buffer, CallbackInfoReturnable<IChatComponent> cir) {
        Screenshots189.saved(cir.getReturnValue());
    }
}
