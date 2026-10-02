// Adapted from FixBookGUI 2.1.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.fixbookgui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

public class FixBookGui {
    public static final String MOD_ID = "fixbookgui";

    public static void init() {
        LogUtils.getLogger().info("FixBookGui initialized");
    }

    public static int getFixedY(Screen screen) {
        return (screen.height - BookViewScreen.IMAGE_HEIGHT) / 3;
    }
}
