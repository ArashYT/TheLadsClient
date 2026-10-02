package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.title.TitleScreenTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Essential's actions as compact Lads buttons in a row at the bottom left, above the account name, on the title and pause
 * screens; and the pause menu's fullscreen toggle (EssentialRow121 on the other versions).
 */
public final class EssentialRow189 {
    private static final List<String> ORDER = Arrays.asList("Social", "Wardrobe", "Pictures", "Host world", "Essential");
    private EssentialRow189() {}

    /**
     * Adds the proxy's action unless the row has one with its label. False while Essential has not bound the proxy's button
     * yet (a moment after the screen opens), so the caller retries.
     */
    public static boolean collect(GuiButton proxy, List<TitleExtrasScreen189.Action> actions) {
        TitleExtrasScreen189.Action action = EssentialActions189.capture(proxy);
        if (action == null) return false;
        for (TitleExtrasScreen189.Action existing : actions) if (existing.label.equals(action.label)) return true;
        actions.add(action);
        return true;
    }

    /** The row's buttons, ids from firstId, for the screen's buttonList. */
    public static List<GuiButton> place(List<TitleExtrasScreen189.Action> actions, int firstId, int height, int maxWidth) {
        List<TitleExtrasScreen189.Action> sorted = new ArrayList<>(actions);
        sorted.sort((a, b) -> Integer.compare(rank(a.label), rank(b.label)));
        int[] widths = new int[sorted.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = Minecraft.getMinecraft().fontRendererObj.getStringWidth(sorted.get(i).label);
        List<TitleScreenTheme.Rect> rects = TitleScreenTheme.essentialRow(height, maxWidth, widths);
        List<GuiButton> row = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            TitleExtrasScreen189.Action action = sorted.get(i);
            TitleScreenTheme.Rect rect = rects.get(i);
            String icon = TitleScreenTheme.essentialIcon(action.label);
            GuiButton button = new CompactButton189(firstId + i, rect.x(), rect.y(), rect.width(), rect.height(), action.label,
                () -> icon, action.press);
            button.enabled = action.enabled;
            row.add(button);
        }
        return row;
    }

    /** The pause menu's top-right fullscreen toggle: Minecraft's own F11. */
    public static GuiButton fullscreen(int id, int screenWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        return new CompactButton189(id, screenWidth - 26, 6, 20, 20, "Fullscreen",
            () -> mc.isFullScreen() ? "windowed" : "fullscreen", mc::toggleFullscreen);
    }

    private static int rank(String label) {
        int i = ORDER.indexOf(label);
        return i < 0 ? ORDER.size() : i;
    }
}
