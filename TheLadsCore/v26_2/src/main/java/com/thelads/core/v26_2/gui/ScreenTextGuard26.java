package com.thelads.core.v26_2.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/** Focused text-box check shared by keyboard, narrator, and friends overlay mixins on 26.2. */
public final class ScreenTextGuard26 {
    private ScreenTextGuard26() {}

    public static boolean isEditingText(Screen screen) {
        if (screen == null) return false;
        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container) {
            focused = container.getFocused();
        }
        if (focused instanceof EditBox editBox && editBox.canConsumeInput()) return true;
        var path = screen.getCurrentFocusPath();
        if (path != null && path.component() instanceof EditBox pathEdit && pathEdit.canConsumeInput()) return true;
        if (screen instanceof LadsSettingsScreen26 ls) return ls.ui().isEditingText();
        return screen instanceof DraggableHudScreen26 ds && ds.ui().isEditingText();
    }
}
