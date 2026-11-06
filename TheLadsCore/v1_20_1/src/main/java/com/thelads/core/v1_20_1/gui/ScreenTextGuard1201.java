package com.thelads.core.v1_20_1.gui;

import com.thelads.core.v1_20_1.social.FriendsScreen1201;
import com.thelads.core.v1_20_1.social.HostScreen1201;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Focused text-box check shared by keyboard and narrator mixins on 1.20.1. */
public final class ScreenTextGuard1201 {
    private ScreenTextGuard1201() {}

    public static boolean isEditingText(Screen screen) {
        if (screen == null) return false;
        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container) {
            focused = container.getFocused();
        }
        if (focused instanceof EditBox editBox && editBox.canConsumeInput()) return true;
        var path = screen.getCurrentFocusPath();
        if (path != null && path.component() instanceof EditBox pathEdit && pathEdit.canConsumeInput()) return true;
        if (screen instanceof FriendsScreen1201 fs) return fs.isEditingText();
        if (screen instanceof HostScreen1201 hs) return hs.isEditingText();
        if (screen instanceof LadsSettingsScreen1201 ls) return ls.ui().isEditingText();
        if (screen instanceof DraggableHudScreen1201 ds) return ds.ui().isEditingText();
        return screen instanceof QuickplayScreen1201;
    }
}
