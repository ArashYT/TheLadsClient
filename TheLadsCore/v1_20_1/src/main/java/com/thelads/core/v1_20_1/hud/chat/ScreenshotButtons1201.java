package com.thelads.core.v1_20_1.hud.chat;

import com.thelads.core.v1_20_1.feature.screenshots.ScreenshotViewerUtils;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.io.File;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Chat's Screenshot Link Buttons: [Copy] [Open File] [Open Folder] [Upload to Imgur] after the "Saved screenshot as ..." message. */
public final class ScreenshotButtons1201 {
    private ScreenshotButtons1201() {}

    public static Component withButtons(Component message) {
        if (!Hud1201.enabled("Chat") || !Hud1201.bool("Chat", "Screenshot Link Buttons", true)) return message;
        if (!(message.getContents() instanceof TranslatableContents success) || !success.getKey().equals("screenshot.success")
            || success.getArgs().length == 0 || !(success.getArgs()[0] instanceof Component name)) return message;
        if (message.getString().contains("[Open File]")) return message;
        ClickEvent open = name.getStyle().getClickEvent();
        if (open == null || open.getAction() != ClickEvent.Action.OPEN_FILE) return message;
        File file = new File(open.getValue());
        return ScreenshotViewerUtils.appendScreenshotButtons(message.copy(), file);
    }
}
