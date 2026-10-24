package com.thelads.core.client.cosmetics;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.cosmetics.anim.Emote;
import com.thelads.core.client.cosmetics.anim.EmoteKeys;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import com.thelads.core.client.cosmetics.net.CosmeticsLink;
import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.modules.CosmeticsModule;
import java.util.List;
import java.util.function.Function;

/**
 * The Cosmetics screen (title screen and pause menu):
 * - Tab 0 (Emotes): which emote sits in which wheel slot, sharing switches, and keybinds.
 * - Tab 1 (Wardrobe): equip wearable cosmetics supported by the backend (capes, hats, glasses slots),
 *   manage cosmetic visibility filters (everyone/friends/self/none), toggle AFK auto-emote, and generate Discord link codes.
 */
public final class CosmeticsScreen {
    record Box(int x, int y, int w, int h) {
        boolean has(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }

    static final int ROW = 14, BUTTON = 20;
    /** Wearables have placeholder art, no catalog ids and a stub Friends Only filter: the tab stays hidden until they are real. */
    public static final boolean WARDROBE = false;
    private final CosmeticsModule cosmetics = CosmeticsModule.get();
    private final Function<String, String> keyLabel;
    private final Runnable onControls, onClose;
    private EmoteWheel wheel;
    private int selected;
    private int tab; // 0 = Emotes, 1 = Wardrobe
    private CosmeticSlot wardrobeSlot = CosmeticSlot.CAPE;

    // Layout boxes
    private Box tabEmotes, tabWardrobe;
    private Box list, clear, reset, others, share, controls, done;
    private Box slotCape, slotHat, slotGlasses;
    private Box linkDiscord, visibilityBtn, afkBtn;
    private int wheelX, wheelY;

    public CosmeticsScreen(Function<String, String> keyLabel, Runnable onControls, Runnable onClose) {
        this.keyLabel = keyLabel;
        this.onControls = onControls;
        this.onClose = onClose;
        refresh();
    }

    private void refresh() { wheel = new EmoteWheel(0, cosmetics.slots()); }

    public int selected() { return selected; }
    public int tab() { return tab; }
    public void setTab(int t) { this.tab = WARDROBE ? t : 0; }

    public void render(LadsGraphics g, int mx, int my) {
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        layout(width, height);
        g.fill(0, 0, width, height, 0xC0070709);

        // Header and Tab Switcher
        g.drawCenteredText(WARDROBE ? "Cosmetics & Wardrobe" : "Cosmetics", width / 2, 6, LadsPalette.TEXT, true);
        if (WARDROBE) {
            tabButton(g, tabEmotes, "Emotes Wheel", tab == 0, mx, my);
            tabButton(g, tabWardrobe, "Wearables Wardrobe", tab == 1, mx, my);
        }

        if (tab == 0) {
            renderEmotes(g, mx, my, width, height);
        } else {
            renderWardrobe(g, mx, my, width, height);
        }
    }

    private void renderEmotes(LadsGraphics g, int mx, int my, int width, int height) {
        String wheelKey = keyLabel.apply(EmoteKeys.WHEEL);
        g.drawCenteredText((wheelKey.isEmpty() ? "Bind Emotes" : "Hold " + wheelKey) + " for the wheel, or give each emote a key (Key Binds)",
            width / 2, 34, LadsPalette.MUTED, false);
        wheel.renderAt(g, wheelX, wheelY, mx, my, selected);

        g.drawText("Slot " + (selected + 1) + ": pick an emote", list.x, list.y - 11, LadsPalette.ACCENT, false);
        String[] slots = cosmetics.slots();
        List<String> ids = EmoteLibrary.ids();
        for (int i = 0; i < ids.size(); i++) {
            Box row = row(i);
            if (row.y + row.h > list.y + list.h) break;
            String id = ids.get(i);
            Emote emote = EmoteLibrary.get(id);
            if (emote == null) continue;
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

    private void renderWardrobe(LadsGraphics g, int mx, int my, int width, int height) {
        g.drawCenteredText("Client-side 3D Wearables (Visual-only, ban-safe on all servers)", width / 2, 34, LadsPalette.MUTED, false);

        // Slot category buttons on left
        String eqCape = CosmeticsManager.get().getLocalEquipped(CosmeticSlot.CAPE);
        CosmeticItem eqCapeItem = CosmeticLibrary.get(eqCape);
        tabButton(g, slotCape, "Capes (" + (eqCapeItem != null ? eqCapeItem.name() : "None") + ")", wardrobeSlot == CosmeticSlot.CAPE, mx, my);

        String eqHat = CosmeticsManager.get().getLocalEquipped(CosmeticSlot.HAT);
        CosmeticItem eqHatItem = CosmeticLibrary.get(eqHat);
        tabButton(g, slotHat, "Hats (" + (eqHatItem != null ? eqHatItem.name() : "None") + ")", wardrobeSlot == CosmeticSlot.HAT, mx, my);

        String eqGlasses = CosmeticsManager.get().getLocalEquipped(CosmeticSlot.GLASSES);
        CosmeticItem eqGlassesItem = CosmeticLibrary.get(eqGlasses);
        tabButton(g, slotGlasses, "Glasses (" + (eqGlassesItem != null ? eqGlassesItem.name() : "None") + ")", wardrobeSlot == CosmeticSlot.GLASSES, mx, my);

        // Item list on right
        g.drawText("Equip " + wardrobeSlot.displayName() + " (click item):", list.x, list.y - 11, LadsPalette.ACCENT, false);

        // Row 0: Unequip
        Box row0 = row(0);
        String currentlyEquipped = CosmeticsManager.get().getLocalEquipped(wardrobeSlot);
        boolean noneEquipped = currentlyEquipped == null || currentlyEquipped.isBlank();
        g.fill(row0.x, row0.y, row0.x + row0.w, row0.y + row0.h, noneEquipped ? LadsPalette.PRIMARY_PRESSED : row0.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawText("[ None / Unequip ]", row0.x + 6, row0.y + (ROW - g.fontHeight()) / 2 + 1, LadsPalette.TEXT, false);

        List<CosmeticItem> items = CosmeticLibrary.getItems(wardrobeSlot);
        for (int i = 0; i < items.size(); i++) {
            Box row = row(i + 1);
            if (row.y + row.h > list.y + list.h) break;
            CosmeticItem item = items.get(i);
            boolean isEq = item.id().equals(currentlyEquipped);
            g.fill(row.x, row.y, row.x + row.w, row.y + row.h, isEq ? LadsPalette.PRIMARY_PRESSED : row.has(mx, my) ? LadsPalette.HOVER : LadsPalette.CARD);
            g.drawText(item.name(), row.x + 6, row.y + (ROW - g.fontHeight()) / 2 + 1, LadsPalette.TEXT, false);

            String tag = isEq ? "Equipped" : item.description();
            g.drawText(tag, row.x + row.w - 6 - g.textWidth(tag), row.y + (ROW - g.fontHeight()) / 2 + 1, isEq ? LadsPalette.ACCENT : LadsPalette.MUTED, false);
        }

        // Link Discord button and display
        String code = CosmeticsManager.get().activeLinkCode();
        String linkLabel = code != null ? "Code: " + code + " (Linked!)" : "Link Discord (Free Cloak)";
        button(g, linkDiscord, linkLabel, mx, my);

        // Visibility filter button
        String visLabel = "Visibility: " + cosmetics.visibility.getChoices()[cosmetics.visibility.getIndex()];
        button(g, visibilityBtn, visLabel, mx, my);

        // AFK emote toggle
        button(g, afkBtn, "AFK Emote: " + (cosmetics.afkEmote.get() ? "On" : "Off"), mx, my);
        button(g, done, "Done", mx, my);

        // Hover tooltip for visibility option
        if (visibilityBtn != null && visibilityBtn.has(mx, my) && cosmetics.visibility.getIndex() == 1) {
            String tip = "Friends Only (needs Lads friends - coming soon)";
            int tw = g.textWidth(tip);
            int tx = Math.max(6, visibilityBtn.x + (visibilityBtn.w - tw) / 2);
            int ty = visibilityBtn.y - g.fontHeight() - 6;
            g.fill(tx - 4, ty - 2, tx + tw + 4, ty + g.fontHeight() + 2, 0xF0151518);
            g.drawText(tip, tx, ty, 0xFFFFAA00, false);
        }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || list == null) return false;

        if (tabEmotes != null && tabEmotes.has(x, y)) { tab = 0; return true; }
        if (WARDROBE && tabWardrobe != null && tabWardrobe.has(x, y)) { tab = 1; return true; }

        if (tab == 0) {
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
        } else {
            if (slotCape != null && slotCape.has(x, y)) { wardrobeSlot = CosmeticSlot.CAPE; return true; }
            if (slotHat != null && slotHat.has(x, y)) { wardrobeSlot = CosmeticSlot.HAT; return true; }
            if (slotGlasses != null && slotGlasses.has(x, y)) { wardrobeSlot = CosmeticSlot.GLASSES; return true; }

            // Row 0 = Unequip
            if (row(0).has(x, y)) {
                CosmeticsManager.get().setLocalEquipped(wardrobeSlot, null);
                return true;
            }

            List<CosmeticItem> items = CosmeticLibrary.getItems(wardrobeSlot);
            for (int i = 0; i < items.size(); i++) {
                if (row(i + 1).has(x, y) && row(i + 1).y + ROW <= list.y + list.h) {
                    CosmeticsManager.get().setLocalEquipped(wardrobeSlot, items.get(i).id());
                    return true;
                }
            }

            if (linkDiscord != null && linkDiscord.has(x, y)) {
                CosmeticsLink.get().requestLinkCode();
                // Reward discord cloak immediately upon link request
                CosmeticsManager.get().setLocalEquipped(CosmeticSlot.CAPE, "lads:cape_discord");
                return true;
            } else if (visibilityBtn != null && visibilityBtn.has(x, y)) {
                cosmetics.visibility.cycle();
                cosmetics.touch();
                return true;
            } else if (afkBtn != null && afkBtn.has(x, y)) {
                cosmetics.afkEmote.set(!cosmetics.afkEmote.get());
                cosmetics.touch();
                return true;
            } else if (done != null && done.has(x, y)) {
                close();
                return true;
            }
            return false;
        }
    }

    /** Saves; the host calls it when the screen closes any way (Esc too) and then leaves. */
    public void close() {
        ConfigManager.save();
        onClose.run();
    }

    private void layout(int width, int height) {
        int bottom = height - BUTTON - 8, gap = 4, side = Math.min(width - 32, 460), left = (width - side) / 2;
        int quarter = (side - 3 * gap) / 4;

        // Tabs at top
        int tabW = 110, tabH = 16, tabY = 17;
        tabEmotes = new Box(width / 2 - tabW - 4, tabY, tabW, tabH);
        tabWardrobe = new Box(width / 2 + 4, tabY, tabW, tabH);

        // Bottom row buttons (Emotes mode)
        others = new Box(left, bottom, quarter, BUTTON);
        share = new Box(left + quarter + gap, bottom, quarter, BUTTON);
        controls = new Box(left + 2 * (quarter + gap), bottom, quarter, BUTTON);
        done = new Box(left + side - quarter, bottom, quarter, BUTTON);

        // Bottom row buttons (Wardrobe mode)
        linkDiscord = new Box(left, bottom, quarter + 20, BUTTON);
        visibilityBtn = new Box(left + quarter + 20 + gap, bottom, quarter + 10, BUTTON);
        afkBtn = new Box(left + 2 * quarter + 30 + 2 * gap, bottom, quarter - 10, BUTTON);

        int top = 56, listX = left + side / 2 + 8, listW = left + side - listX;
        wheelX = left + side / 4;
        wheelY = (top - 8 + bottom - 6) / 2;
        int halfW = (listW - gap) / 2, buttonsY = bottom - 8 - BUTTON;
        clear = new Box(listX, buttonsY, halfW, BUTTON);
        reset = new Box(listX + listW - halfW, buttonsY, halfW, BUTTON);
        list = new Box(listX, top, listW, buttonsY - 6 - top);

        // Wardrobe category buttons on the left
        int slotW = listX - left - 16;
        int slotH = 22;
        slotCape = new Box(left + 4, top + 10, slotW, slotH);
        slotHat = new Box(left + 4, top + 10 + slotH + 8, slotW, slotH);
        slotGlasses = new Box(left + 4, top + 10 + 2 * (slotH + 8), slotW, slotH);
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
        if (b == null) return;
        boolean hover = b.has(mx, my);
        TitleScreenTheme.renderButtonSurface(g, b.x, b.y, b.w, b.h, hover, false, true, 1);
        g.drawCenteredText(label, b.x + b.w / 2, b.y + (b.h - g.fontHeight()) / 2 + 1, hover ? LadsPalette.TEXT : LadsPalette.MUTED, false);
    }

    private static void tabButton(LadsGraphics g, Box b, String label, boolean active, int mx, int my) {
        if (b == null) return;
        boolean hover = b.has(mx, my);
        int bg = active ? LadsPalette.PRIMARY_PRESSED : hover ? LadsPalette.HOVER : LadsPalette.CARD;
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        g.drawCenteredText(label, b.x + b.w / 2, b.y + (b.h - g.fontHeight()) / 2 + 1, active ? LadsPalette.TEXT : LadsPalette.MUTED, false);
    }
}
