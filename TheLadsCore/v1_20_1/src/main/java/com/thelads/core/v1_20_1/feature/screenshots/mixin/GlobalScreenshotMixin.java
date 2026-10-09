package com.thelads.core.v1_20_1.feature.screenshots.mixin;

import com.thelads.core.v1_20_1.feature.GlobalScreenshots1201;
import java.io.File;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every screenshot is written into the shared screenshots folder; Screenshot.grab adds "screenshots" to the folder it is given. */
@Mixin(Screenshot.class)
public class GlobalScreenshotMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static File ladsGlobalScreenshots(File original) {
        return GlobalScreenshots1201.gameDirectory();
    }
}
