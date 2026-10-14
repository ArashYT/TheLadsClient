// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core, ported to 1.20.1.
package com.thelads.core.v1_20_1.embedded.fixbookgui;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

public class FixBookGui {
    public static final String MOD_ID = "fixbookgui";

    /** Height of the book texture (BookViewScreen.IMAGE_HEIGHT, which is protected in 1.20.1). */
    private static final int IMAGE_HEIGHT = 192;

    private static final Map<Screen, Integer> APPLIED_SHIFT = new WeakHashMap<>();

    /**
     * How far below the top of the screen the book is moved: a third of the space the book leaves free, never negative.
     * When foreign widgets or screens exist (e.g. Amendments' lectern editor), returns 0 to avoid overlapping custom widgets.
     */
    public static synchronized int getFixedY(Screen screen) {
        if (hasForeignWidgets(screen)) {
            return 0;
        }
        return Math.max(0, (screen.height - IMAGE_HEIGHT) / 3);
    }

    /**
     * Returns true if the screen is a foreign subclass or contains widgets added by another mod.
     */
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

    public static synchronized void resetShift(Screen screen) {
        if (screen != null) {
            APPLIED_SHIFT.remove(screen);
        }
    }

    public static synchronized void ensureWidgetsShifted(Screen screen) {
        if (screen == null) return;
        int target = getFixedY(screen);
        int current = APPLIED_SHIFT.getOrDefault(screen, 0);
        if (current != target) {
            int delta = target - current;
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    widget.setY(widget.getY() + delta);
                }
            }
            APPLIED_SHIFT.put(screen, target);
        }
    }

    /**
     * Vanilla 1.20.1 places the book's buttons at fixed heights (196, 159) in init(); move every widget the init created
     * (Done, page arrows, Sign/Cancel/Finalize, the lectern's Take Book) by the same amount as the book.
     */
    public static synchronized void shiftWidgets(Screen screen) {
        resetShift(screen);
        ensureWidgetsShifted(screen);
    }
}
