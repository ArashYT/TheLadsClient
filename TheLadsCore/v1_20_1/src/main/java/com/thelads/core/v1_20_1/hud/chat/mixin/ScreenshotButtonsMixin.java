package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.ScreenshotButtons1201;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat's Screenshot Link Buttons: the message about a saved screenshot is sent on with [Open File] [Open Folder] after it. */
@Mixin(Screenshot.class)
public abstract class ScreenshotButtonsMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true, require = 1)
    private static Consumer<Component> lads$buttons(Consumer<Component> chat) {
        return message -> chat.accept(ScreenshotButtons1201.withButtons(message));
    }
}
