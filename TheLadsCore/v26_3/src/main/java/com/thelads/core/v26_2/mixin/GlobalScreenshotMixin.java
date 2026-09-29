package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Screenshot;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Screenshot.class)
public class GlobalScreenshotMixin {
    @ModifyVariable(method="grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private static File ladsGlobalScreenshots(File original){return com.thelads.core.v26_2.feature.GlobalScreenshots.gameDirectory();}
}
