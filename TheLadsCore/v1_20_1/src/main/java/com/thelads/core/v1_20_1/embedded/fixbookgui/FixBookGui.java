// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core, ported to 1.20.1.
package com.thelads.core.v1_20_1.embedded.fixbookgui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

public class FixBookGui {
    public static final String MOD_ID = "fixbookgui";

    /** Height of the book texture (BookViewScreen.IMAGE_HEIGHT, which is protected in 1.20.1). */
    private static final int IMAGE_HEIGHT = 192;

    /**
     * How far below the top of the screen the book is moved: a third of the space the book leaves free, never negative.
     * (26.3 uses the plain {@code (height - 192) / 3}; below 192 high that would push the book's top off the screen.)
     */
    public static int getFixedY(Screen screen) {
        return Math.max(0, (screen.height - IMAGE_HEIGHT) / 3);
    }

    /**
     * Vanilla 1.20.1 places the book's buttons at fixed heights (196, 159) in init(); move every widget the init created
     * (Done, page arrows, Sign/Cancel/Finalize, the lectern's Take Book) by the same amount as the book.
     */
    public static void shiftWidgets(Screen screen) {
        int dy = getFixedY(screen);
        if (dy == 0) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) widget.setY(widget.getY() + dy);
        }
    }
}
