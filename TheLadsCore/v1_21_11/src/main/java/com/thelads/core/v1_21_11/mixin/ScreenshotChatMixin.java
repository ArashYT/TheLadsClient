package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import com.thelads.core.v1_21_11.gui.Screenshots12111;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat "Screenshot Link Buttons": [Copy] [Open File] [Open Folder] [Upload to Imgur] after the screenshot message. */
@Mixin(Screenshot.class)
public abstract class ScreenshotChatMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true, require = 1)
    private static Consumer<Component> ladsScreenshotButtons(Consumer<Component> chat) {
        return message -> chat.accept(ladsWithButtons(message));
    }

    @Unique
    private static Component ladsWithButtons(Component message) {
        if (!NativeQualityOfLife.enabled("Chat") || !NativeQualityOfLife.bool("Chat", "Screenshot Link Buttons", true)) return message;
        if (!(message.getContents() instanceof TranslatableContents success) || !success.getKey().equals("screenshot.success")
            || success.getArgs().length == 0 || !(success.getArgs()[0] instanceof Component name)) return message;
        File file = ladsResolveFile(name);
        if (file == null) return message;
        return Screenshots12111.appendScreenshotButtons(message.copy(), file);
    }

    @Unique
    private static File ladsResolveFile(Component name) {
        ClickEvent click = name.getStyle().getClickEvent();
        if (click instanceof ClickEvent.OpenFile open) {
            return open.file();
        }
        if (click != null) {
            for (String method : new String[]{"getScreenshotFile", "screenshotFile", "file"}) {
                try {
                    Object res = click.getClass().getMethod(method).invoke(click);
                    if (res instanceof File f) return f;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        String fileName = name.getString();
        if (fileName != null && !fileName.isBlank()) {
            return new File(new File(Minecraft.getInstance().gameDirectory, "screenshots"), fileName);
        }
        return null;
    }
}
