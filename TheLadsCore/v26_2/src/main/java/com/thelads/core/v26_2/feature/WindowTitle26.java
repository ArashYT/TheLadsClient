package com.thelads.core.v26_2.feature;

import com.thelads.core.client.WindowTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

public final class WindowTitle26 {
    private static final WindowTitle.Updater UPDATER = new WindowTitle.Updater("26.2", title -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            mc.getWindow().setTitle(title);
        }
    });

    private WindowTitle26() {}

    public static void refresh(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return;
        if (false) return;
        boolean inWorld = mc.level != null;
        boolean localWorld = inWorld && (mc.isLocalServer() || mc.getSingleplayerServer() != null);
        var remote = mc.getCurrentServer();
        Screen screen = mc.gui == null ? null : mc.gui.screen();
        WindowTitle.Menu menu = screen == null ? WindowTitle.Menu.NONE
            : screen instanceof TitleScreen ? WindowTitle.Menu.TITLE
            : screen instanceof SelectWorldScreen ? WindowTitle.Menu.WORLD_SELECT
            : screen instanceof JoinMultiplayerScreen ? WindowTitle.Menu.SERVER_LIST
            : WindowTitle.Menu.OTHER;
        UPDATER.update(inWorld, localWorld, remote == null ? null : remote.ip, menu);
    }
}
