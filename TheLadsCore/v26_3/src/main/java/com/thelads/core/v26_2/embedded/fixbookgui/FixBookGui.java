// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.fixbookgui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

public class FixBookGui {
    public static final String MOD_ID = "fixbookgui";

    public static void init() {
        LogUtils.getLogger().info("FixBookGui initialized");
    }

    public static int getFixedY(Screen screen) {
        if (hasForeignWidgets(screen)) {
            return 0;
        }
        return Math.max(0, (screen.height - BookViewScreen.IMAGE_HEIGHT) / 3);
    }

    public static boolean hasForeignWidgets(Screen screen) {
        if (screen == null) return false;
        String screenClass = screen.getClass().getName();
        if (!screenClass.startsWith("net.minecraft.") && !screenClass.startsWith("com.thelads.core.")) {
            return true;
        }
        for (GuiEventListener child : screen.children()) {
            if (child != null) {
                String childClass = child.getClass().getName();
                if (!childClass.startsWith("net.minecraft.") && !childClass.startsWith("com.thelads.core.")) {
                    return true;
                }
            }
        }
        return false;
    }
}
