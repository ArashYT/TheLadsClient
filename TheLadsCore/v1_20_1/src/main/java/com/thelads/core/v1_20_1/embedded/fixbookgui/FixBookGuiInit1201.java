// Adapted from FixBookGUI 2.2.0 by KosmoMoustache (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core, ported to 1.20.1.
package com.thelads.core.v1_20_1.embedded.fixbookgui;

import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.LoggerFactory;

/** Entry point called by EmbeddedMods1201 while the original FixBookGUI is not installed. */
public final class FixBookGuiInit1201 {
    private FixBookGuiInit1201() {}

    public static void init() {
        LoggerFactory.getLogger("FixBookGui").info("FixBookGui initialized");
        SmallModsProbe1201.runOnce("fixbookgui", true, FixBookGuiInit1201::probe);
    }

    /**
     * Lays out the real book screens (not shown) on a 400 x 300 gui: the book is moved down by (300 - 192) / 3 = 36, so the Done
     * and Sign buttons that vanilla puts at y = 196 are at 232 and the page arrows (159) at 195.
     */
    private static void probe(Minecraft client) {
        int width = 400, height = 300, shift = (height - 192) / 3;
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        BookViewScreen view = new BookViewScreen(BookViewScreen.BookAccess.fromItem(book));
        view.init(client, width, height);
        BookEditScreen edit = new BookEditScreen(client.player, book, InteractionHand.MAIN_HAND);
        edit.init(client, width, height);
        List<Integer> viewYs = widgetYs(view), editYs = widgetYs(edit);
        boolean vanillaOk = !viewYs.isEmpty() && !editYs.isEmpty() && viewYs.stream().allMatch(y -> y == 159 + shift || y == 196 + shift)
            && editYs.stream().allMatch(y -> y == 159 + shift || y == 196 + shift)
            && viewYs.contains(196 + shift) && editYs.contains(196 + shift) && editYs.contains(159 + shift);

        Screen foreignScreen = new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)) {};
        boolean foreignOk = FixBookGui.hasForeignWidgets(foreignScreen) && FixBookGui.getFixedY(foreignScreen) == 0;

        boolean ok = vanillaOk && foreignOk;
        SmallModsProbe1201.result("fixbookgui", ok, "gui " + width + "x" + height + " shift=" + shift + " (expected button y " + (159 + shift) + " / " + (196 + shift)
            + ") viewScreenWidgetY=" + viewYs + " editScreenWidgetY=" + editYs + " foreignShift=" + FixBookGui.getFixedY(foreignScreen));
    }

    private static List<Integer> widgetYs(Screen screen) {
        List<Integer> ys = new ArrayList<>();
        for (GuiEventListener child : screen.children()) if (child instanceof AbstractWidget widget) ys.add(widget.getY());
        return ys;
    }
}
