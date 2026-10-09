package com.thelads.core.v1_20_1.hud.chat;

import com.thelads.core.v1_20_1.hud.Hud1201;
import java.io.File;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Chat's Screenshot Link Buttons: [Open File] [Open Folder] after the "Saved screenshot as ..." message. */
public final class ScreenshotButtons1201 {
    private ScreenshotButtons1201() {}

    public static Component withButtons(Component message) {
        if (!Hud1201.enabled("Chat") || !Hud1201.bool("Chat", "Screenshot Link Buttons", true)) return message;
        if (!(message.getContents() instanceof TranslatableContents success) || !success.getKey().equals("screenshot.success")
            || success.getArgs().length == 0 || !(success.getArgs()[0] instanceof Component name)) return message;
        ClickEvent open = name.getStyle().getClickEvent();
        if (open == null || open.getAction() != ClickEvent.Action.OPEN_FILE) return message;
        File file = new File(open.getValue());
        MutableComponent buttons = message.copy().append(button(" [Open File]", ChatFormatting.GREEN, file, "Open screenshot file"));
        File folder = file.getParentFile();
        return folder == null ? buttons : buttons.append(button(" [Open Folder]", ChatFormatting.AQUA, folder, "Open screenshots folder"));
    }

    private static MutableComponent button(String label, ChatFormatting color, File target, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(color)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, target.getAbsolutePath()))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }
}
