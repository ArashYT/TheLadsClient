package com.thelads.core.client.cosmetics;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteKeys;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.modules.CosmeticsModule;
import java.util.List;
import java.util.function.Function;

/**
 * The Cosmetics screen (title screen and pause menu): which emote sits in which wheel slot, the two sharing switches, and a way to
 * Controls, where every emote has its own key. Pick a slot on the wheel, then an emote from the list to put it there.
 * Each version hosts it in a plain screen and forwards render, click and close; {@code keyLabel} turns a key binding name
 * (EmoteKeys.WHEEL, EmoteKeys.keyName) into the bound key's label, "" when unbound.
 */
public final class CosmeticsScreen {
    record Box(int x, int y, int w, int h) {
        boolean has(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }

    static final int ROW = 14, BUTTON = 20; // eight rows fit a 240-high screen (854x480 at GUI scale 2)
    private final CosmeticsModule cosmetics = CosmeticsModule.get();
    private final Function<String, String> keyLabel;
    private final Runnable onControls, onClose;
    private EmoteWheel wheel;
    private int selected;
    // Laid out each frame (render), read by the next click.
    private Box list, clear, reset, others, share, controls, done;
    private int wheelX, wheelY;

    public CosmeticsScreen(Function<String, String> keyLabel, Runnable onControls, Runnable onClose) {
        this.keyLabel = keyLabel;
        this.onControls = onControls;
        this.onClose = onClose;
        refresh();
    }

    private void refresh() { wheel = new EmoteWheel(0, cosmetics.slots()); }

    public int selected() { return selected; }

    public void render(LadsGraphics g, int mx, int my) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        layout(width, height);
        g.fill(0, 0, width, height, 0xC0070709);
        g.drawCenteredText("Cosmetics", width / 2, 8, LadsPalette.TEXT, true);
        String wheelKey = keyLabel.apply(EmoteKeys.WHEEL);
        g.drawCenteredText((wheelKey.isEmpty() ? "Bind Emotes in Controls" : "Hold " + wheelKey) + " for the emote wheel. Every emote also has its own key.",
            width / 2, 20, LadsPalette.MUTED, false);
        wheel.renderAt(g, wheelX, wheelY, mx, my, selected);

        g.drawText("Slot " + (selected + 1) + ": pick an emote", list.x, list.y - 11, LadsPalette.ACCENT, false);
        String[] slots = cosmetics.slots();
        List<String> ids = EmoteLibrary.ids();
        // ponytail: no scrolling, the built-in library fits; add a scroll offset when emotes outgrow the panel.
        for (int i = 0; i < ids.size(); i++) {
            Box row = row(i);
            if (row.y + row.h > list.y + list.h) break;
            String id = ids.get(i);
            Emote emote = EmoteLibrary.get(id);
            boolean here = id.equals(slots[selected]);
            g.fill(row.x, row.y, row.x + row.w, row.y + row.h, here ? LadsPalette.PRIMARY_PRESSED : row.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
            g.drawText(emote.name, row.x + 6, row.y + (ROW - g.fontHeight()) / 2 + 1, LadsPalette.TEXT, false);
            int slot = indexOf(slots, id);
            String key = keyLabel.apply(EmoteKeys.keyName(id)), tag = (slot < 0 ? "" : "Slot " + (slot + 1) + "  ") + (key.isEmpty() ? "No key" : key);
            g.drawText(tag, row.x + row.w - 6 - g.textWidth(tag), row.y + (ROW - g.fontHeight()) / 2 + 1, LadsPalette.MUTED, false);
        }
        button(g, clear, "Clear slot", mx, my);
        button(g, reset, "Reset wheel", mx, my);
        button(g, others, "Show others: " + (cosmetics.showOthers.get() ? "On" : "Off"), mx, my);
        button(g, share, "Share mine: " + (cosmetics.shareMine.get() ? "On" : "Off"), mx, my);
        button(g, controls, "Key Binds", mx, my);
        button(g, done, "Done", mx, my);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || list == null) return false;
        int hit = wheel.hovered(x, y);
        double dx = x - wheelX, dy = y - wheelY;
        if (hit >= 0 && dx * dx + dy * dy <= EmoteWheel.OUTER * EmoteWheel.OUTER) { selected = hit; return true; }
        List<String> ids = EmoteLibrary.ids();
        for (int i = 0; i < ids.size(); i++) {
            if (row(i).has(x, y) && row(i).y + ROW <= list.y + list.h) { cosmetics.setSlot(selected, ids.get(i)); refresh(); return true; }
        }
        if (clear.has(x, y)) { cosmetics.setSlot(selected, null); refresh(); }
        else if (reset.has(x, y)) { cosmetics.wheel.set(""); cosmetics.touch(); refresh(); }
        else if (others.has(x, y)) cosmetics.showOthers.set(!cosmetics.showOthers.get());
        else if (share.has(x, y)) cosmetics.shareMine.set(!cosmetics.shareMine.get());
        else if (controls.has(x, y)) { ConfigManager.save(); onControls.run(); }
        else if (done.has(x, y)) close();
        else return false;
        return true;
    }

    /** Saves; the host calls it when the screen closes any way (Esc too) and then leaves. */
    public void close() {
        ConfigManager.save();
        onClose.run();
    }

    private void layout(int width, int height) {
        int bottom = height - BUTTON - 8, gap = 4, side = Math.min(width - 32, 460), left = (width - side) / 2;
        int quarter = (side - 3 * gap) / 4;
        others = new Box(left, bottom, quarter, BUTTON);
        share = new Box(left + quarter + gap, bottom, quarter, BUTTON);
        controls = new Box(left + 2 * (quarter + gap), bottom, quarter, BUTTON);
        done = new Box(left + side - quarter, bottom, quarter, BUTTON);
        int top = 44, listX = left + side / 2 + 8, listW = left + side - listX;
        wheelX = left + side / 4;
        wheelY = (top - 8 + bottom - 6) / 2;
        int halfW = (listW - gap) / 2, buttonsY = bottom - 8 - BUTTON;
        clear = new Box(listX, buttonsY, halfW, BUTTON);
        reset = new Box(listX + listW - halfW, buttonsY, halfW, BUTTON);
        list = new Box(listX, top, listW, buttonsY - 6 - top);
    }

    private Box row(int i) { return new Box(list.x, list.y + i * (ROW + 2), list.w, ROW); }

    int wheelX() { return wheelX; }
    int wheelY() { return wheelY; }
    Box rowBox(int i) { return row(i); }

    private static int indexOf(String[] slots, String id) {
        for (int i = 0; i < slots.length; i++) if (id.equals(slots[i])) return i;
        return -1;
    }

    private static void button(LadsGraphics g, Box b, String label, int mx, int my) {
        boolean hover = b.has(mx, my);
        TitleScreenTheme.renderButtonSurface(g, b.x, b.y, b.w, b.h, hover, false, true, 1);
        g.drawCenteredText(label, b.x + b.w / 2, b.y + (b.h - g.fontHeight()) / 2 + 1, hover ? LadsPalette.TEXT : LadsPalette.MUTED, false);
    }
}
