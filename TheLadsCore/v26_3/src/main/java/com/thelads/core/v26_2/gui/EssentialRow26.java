package com.thelads.core.v26_2.gui;

import com.thelads.core.client.title.TitleScreenTheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Essential's actions as compact Lads buttons in a row at the bottom left, above the account card (title and pause). */
public final class EssentialRow26 {
    private static final List<String> ORDER = List.of("Social", "Wardrobe", "Pictures", "Host world", "Essential");
    private EssentialRow26() {}

    /**
     * Adds the widget's action unless the row has one with its label. False while Essential has not bound the widget's
     * button yet (a moment after the screen opens), so the caller retries.
     */
    public static boolean collect(net.minecraft.client.gui.screens.Screen screen, AbstractWidget widget, List<EssentialActions.Action> actions) {
        var action = EssentialActions.capture(screen, widget);
        if (action == null) return false;
        if (actions.stream().noneMatch(a -> a.label().equals(action.label()))) actions.add(action);
        return true;
    }

    /** Creates the row's buttons, hands each to {@code add} (the screen's addRenderableWidget) and returns them. */
    public static List<AbstractWidget> place(Consumer<AbstractWidget> add, List<EssentialActions.Action> actions,
                                             int height, int maxWidth) {
        var sorted = actions.stream().sorted(Comparator.comparingInt(a -> {
            int i = ORDER.indexOf(a.label());
            return i < 0 ? ORDER.size() : i;
        })).toList();
        var font = Minecraft.getInstance().font;
        var rects = TitleScreenTheme.essentialRow(height, maxWidth, sorted.stream().mapToInt(a -> font.width(a.label())).toArray());
        List<AbstractWidget> row = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            var action = sorted.get(i);
            var rect = rects.get(i);
            var button = new CompactButton26(rect.x(), rect.y(), rect.width(), rect.height(), Component.literal(action.label()),
                () -> TitleScreenTheme.essentialIcon(action.label()), b -> action.press().run());
            button.active = action.active();
            button.setTabOrderGroup(100 + i);
            add.accept(button);
            row.add(button);
        }
        return row;
    }
}
