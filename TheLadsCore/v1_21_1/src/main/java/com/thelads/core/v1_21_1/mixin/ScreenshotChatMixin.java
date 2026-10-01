package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.v1_21_1.feature.NativeQualityOfLife;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat "Screenshot Link Buttons": [Open File] [Open Folder] after the screenshot message (26.x adds Copy/Imgur through its Screenshot Viewer). */
@Mixin(Screenshot.class)
public abstract class ScreenshotChatMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true, require = 1)
    private static Consumer<Component> ladsScreenshotButtons(Consumer<Component> chat) {
        return message -> chat.accept(ladsWithButtons(message));
    }

    @Unique
    private static Component ladsWithButtons(Component message) {
        if (!NativeQualityOfLife.enabled("Chat") || !NativeQualityOfLife.bool("Chat", "Screenshot Link Buttons", true)) return message;
        if (!(message.getContents() instanceof TranslatableContents success) || !success.getKey().equals("screenshot.success")
            || success.getArgs().length == 0 || !(success.getArgs()[0] instanceof Component name)) return message;
        ClickEvent open = name.getStyle().getClickEvent();
        if (open == null || open.getAction() != ClickEvent.Action.OPEN_FILE) return message;
        File file = new File(open.getValue());
        return message.copy().append(ladsButton(" [Open File]", ChatFormatting.GREEN, file, "Open screenshot file"))
            .append(ladsButton(" [Open Folder]", ChatFormatting.AQUA, file.getParentFile(), "Open screenshots folder"));
    }

    @Unique
    private static MutableComponent ladsButton(String label, ChatFormatting color, File target, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(color)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, target.getAbsolutePath()))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }
}
