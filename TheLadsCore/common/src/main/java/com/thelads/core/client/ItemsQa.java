package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, the same on every version (each version's items probe drives it through a Driver; inert otherwise): the 1.9.1 items
 * checks. 1. The items drawn by block entity renderers (chests first): in the inventory, held in first and third person, in item
 * frames, on armour stands and dropped, photographed. 2. Hotbar keys in inventories: a hotbar key on Mouse 4 swaps the hovered
 * slot (inventory, chest, creative), a keyboard hotbar key while another is on the mouse, Swap Item With Offhand on Mouse 5 and on F
 * (where there is an offhand); checked slot by slot. 3. Tooltips too big for the screen are drawn smaller (TooltipFit.last), a
 * normal one is not; with Enhanced Tooltips' NBT lines and with potion effects (the centred inventory). Frames: items-*.png. Logs
 * "Lads items check PASS/FAIL", ends with "Lads items END: N passed, M failed".
 */
public final class ItemsQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    /** What a version does for the sequence. Item names are ids without "minecraft:" (1.8.9: "skull:3" for metadata 3). */
    public interface Driver {
        /** This version's items drawn by block entity renderers, chests first, then plain items that look alike. */
        List<String> specialItems();
        /** Survival (false) or creative (true), set by the integrated server. */
        void creative(boolean creative);
        /**
         * True: peaceful (nothing hurts the survival QA player; 1.8.9's QA world spawns slimes), midday, the player looking level
         * to the north. False: the difficulty it had.
         */
        void prepare(boolean on);
        /** Speed and Night Vision for five minutes (true), or no effects. */
        void effects(boolean on);
        /** Empties the player's inventory: hotbar, main, armour, offhand, cursor. */
        void clear();
        /** One {@code item} (null: nothing) in player inventory slot {@code slot} (0-8 hotbar, 9-35 main), set by the server. */
        void give(int slot, String item);
        /** A stone named "QA tooltip" with {@code lines} lore lines of {@code length} characters, in that slot. */
        void giveTall(int slot, int lines, int length);
        /** The item id in player inventory slot {@code slot} (40: offhand) as the client has it; "" when empty. */
        String item(int slot);
        void select(int hotbar);
        /** 0 first person, 2 third person from the front. */
        void view(int view);
        /** Item frames, armour stands (holding and wearing) and dropped copies of these items before the player, who faces them; null removes them. */
        void showcase(List<String> items);
        /** The player's inventory screen (the creative one in creative mode). */
        void openInventory();
        /** A chest holding {@code item} in its first slot, opened through the server. */
        void openChest(String item);
        void closeScreen();
        /** The pointer, as a real mouse moves it, over the open screen's slot of player inventory slot {@code slot}. */
        void hoverInventory(int slot);
        /** The pointer over the open screen's menu slot {@code index} (a chest's or the creative grid's); the item id there. */
        String hoverMenu(int index);
        /** Mouse 4 (back) or Mouse 5 (forward) pressed and released through the game's own mouse handler. */
        void mouse(int button);
        /** A keyboard key ("4", "f") pressed and released through the game's own key handler. */
        void key(String key);
        /** Hotbar slot 0-8 (-1: Swap Item With Offhand) bound to "mouse4", "mouse5" or a key name; null: every bind back as it was. */
        void bind(int hotbar, String key);
        boolean offhand();
        /** Writes the next finished frame to screenshots/name.png. */
        void shot(String name);
    }

    /** Returns the ticks to wait before the next step, or {@link #AGAIN} to run again next tick (a condition not met yet). */
    private interface Step { int run(Driver d); }

    private static final int AGAIN = -1;

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static int next = -1, wait, passed, cleanup, tries;
    private static boolean tooltipsWere;
    private static long modifiedWere;

    private ItemsQa() {}

    public static boolean running() { return next >= 0 && next < STEPS.size(); }

    public static int failed() { return FAILURES.size(); }

    /** Each client tick of the probe: plans the sequence, then steps it; true once it has ended. */
    public static boolean tick(Driver d) {
        if (next < 0) {
            plan(d);
            next = 0;
            LOGGER.info("Lads items BEGIN: {} block entity items {}, offhand {}", d.specialItems().size(), d.specialItems(), d.offhand());
        }
        if (wait > 0) { wait--; return false; }
        if (next >= STEPS.size()) return true;
        int at = next++;
        try {
            wait = STEPS.get(at).run(d);
            if (wait == AGAIN) {
                if (++tries > 400) throw new IllegalStateException("waited 20 s for step " + at);
                next = at;
                wait = 0;
            } else tries = 0;
        } catch (RuntimeException failure) {
            FAILURES.add("step " + at + ": " + failure);
            LOGGER.error("Lads items check FAIL at step {}", at, failure);
            if (at < cleanup) next = cleanup;
        }
        if (next < STEPS.size()) return false;
        LOGGER.info("Lads items END: {} passed, {} failed", passed, FAILURES.size());
        if (!FAILURES.isEmpty()) LOGGER.error("Lads items FAILED: {}", FAILURES);
        return true;
    }

    private static void add(Step step) { STEPS.add(step); }

    private static void plan(Driver d) {
        List<String> items = d.specialItems();
        // 1. The block entity items, every way the game draws an item.
        add(x -> { d.prepare(true); d.closeScreen(); d.creative(false); d.effects(false); d.bind(0, null); d.view(0); d.clear(); return 20; });
        int last = Math.min(items.size(), 36) - 1;
        add(x -> { for (int i = 0; i <= last; i++) d.give(i, items.get(i)); d.select(8); return 2; });
        // The client must have every one, main inventory included (1.20.1: Better Statistics Screen's off-screen creative menu took them).
        add(until("the client's inventory shows all " + (last + 1) + " items given (hotbar and main inventory)", () -> {
            for (int i = 0; i <= last; i++) if (!d.item(i).equals(items.get(i).split(":")[0])) return false;
            return true;
        }));
        add(x -> { d.openInventory(); return 30; });
        add(x -> { d.hoverInventory(35); return 10; }); // the pointer off the items (the last main slot)
        add(x -> shot(d, "items-1-inventory"));
        add(x -> { d.closeScreen(); d.clear(); return 10; });
        for (String item : items) {
            add(x -> { d.give(0, item); d.select(0); return 2; });
            add(x -> d.item(0).equals(item.split(":")[0]) ? 15 : AGAIN); // the client has it, then the hand's equip animation
            add(x -> shot(d, "items-2-held-" + file(item)));
        }
        add(x -> { d.view(2); return 10; });
        for (String item : items.subList(0, Math.min(3, items.size()))) { // the chests from the front, in third person
            add(x -> { d.give(0, item); return 2; });
            add(x -> d.item(0).equals(item.split(":")[0]) ? 15 : AGAIN);
            add(x -> shot(d, "items-3-third-person-" + file(item)));
        }
        add(x -> { d.view(0); d.clear(); d.showcase(items); return 60; });
        add(x -> shot(d, "items-4-showcase"));
        add(x -> { d.showcase(null); return 10; });

        // 2. Hotbar keys in inventories: hotbar 3 on Mouse 4, hotbar 4 on its key, Swap Item With Offhand on Mouse 5 then F.
        add(x -> { d.give(9, "stone"); d.bind(2, "mouse4"); if (d.offhand()) d.bind(-1, "mouse5"); return 20; });
        add(x -> { d.openInventory(); return 30; });
        add(x -> { d.hoverInventory(9); return 5; });
        add(x -> { d.mouse(4); return 10; });
        add(x -> { expect(d, "inventory: Mouse 4 (bound to hotbar 3) swaps the hovered stone into hotbar 3", 2, "stone", 9, ""); return shot(d, "items-5-mouse4-swapped"); });
        add(x -> { d.hoverInventory(2); return 5; });
        add(x -> { d.key("4"); return 10; });
        add(x -> { expect(d, "inventory: the 4 key (hotbar 4) still swaps while hotbar 3 is on Mouse 4", 3, "stone", 2, ""); return 1; });
        if (d.offhand()) {
            add(x -> { d.hoverInventory(3); return 5; });
            add(x -> { d.mouse(5); return 10; });
            add(x -> { expect(d, "inventory: Mouse 5 (bound to Swap Item With Offhand) moves the hovered stone to the offhand", 40, "stone", 3, ""); return 1; });
            add(x -> { d.bind(-1, "f"); d.hoverInventory(10); return 5; });
            add(x -> { d.key("f"); return 10; });
            add(x -> { expect(d, "inventory: F (Swap Item With Offhand) swaps the offhand into the hovered slot", 10, "stone", 40, ""); return 1; });
        }
        add(x -> { d.closeScreen(); d.clear(); return 10; });
        add(x -> { d.openChest("diamond"); return 30; });
        add(x -> { d.hoverMenu(0); return 5; });
        add(x -> { d.mouse(4); return 10; });
        add(x -> { expect(d, "chest: Mouse 4 moves the chest's hovered diamond to hotbar 3", 2, "diamond"); return shot(d, "items-6-chest-mouse4"); });
        add(x -> { d.closeScreen(); d.clear(); d.creative(true); return 20; });
        add(x -> { d.openInventory(); return 30; });
        String[] picked = new String[2];
        add(x -> { picked[0] = d.hoverMenu(0); return 5; });
        add(x -> { d.mouse(4); return 10; });
        add(x -> { expect(d, "creative: Mouse 4 puts the hovered " + picked[0] + " in hotbar 3", 2, picked[0]); return 1; });
        add(x -> { picked[1] = d.hoverMenu(1); return 5; });
        add(x -> { d.key("4"); return 10; });
        add(x -> { expect(d, "creative: the 4 key puts the hovered " + picked[1] + " in hotbar 4", 3, picked[1]); return shot(d, "items-7-creative"); });
        add(x -> { d.closeScreen(); d.bind(0, null); d.clear(); d.creative(false); return 20; });

        // 3. Tooltips too big for the screen, with Enhanced Tooltips' NBT lines on.
        add(x -> { tooltips(true); d.giveTall(0, 30, 24); d.give(1, "diamond"); d.giveTall(2, 1, 110); return 20; });
        add(x -> { d.openInventory(); return 30; });
        tooltip(d, 0, "items-8-tooltip-tall", true);
        tooltip(d, 1, "items-9-tooltip-normal", false);
        tooltip(d, 2, "items-10-tooltip-wide", true);
        add(x -> { d.closeScreen(); d.effects(true); return 40; });
        add(x -> { d.openInventory(); return 30; });
        tooltip(d, 0, "items-11-tooltip-tall-effects", true);

        cleanup = STEPS.size();
        add(x -> { d.closeScreen(); d.bind(0, null); d.effects(false); d.showcase(null); d.clear(); d.view(0); tooltips(false); d.prepare(false); return 10; });
    }

    /** A step that waits (up to 15 s: a loaded QA machine's server lags) until {@code ok}, then checks it. */
    private static Step until(String what, java.util.function.BooleanSupplier ok) {
        return x -> {
            if (ok.getAsBoolean() || tries >= 300) { check(ok.getAsBoolean(), what); return 10; }
            return AGAIN;
        };
    }

    /** Hovers player inventory slot {@code slot} and checks the tooltip drawn there (scaled down or not) in a frame. */
    private static void tooltip(Driver d, int slot, String shot, boolean scaled) {
        add(x -> { TooltipFit.last = -1F; d.hoverInventory(slot); return 10; });
        add(x -> {
            float scale = TooltipFit.last;
            check(scaled ? scale >= 0.5F && scale < 1F : scale == 1F, shot + ": the tooltip is drawn at scale " + scale
                + (scaled ? " (smaller, to fit the screen)" : " (as it is)"));
            return shot(d, shot);
        });
    }

    /** Enhanced Tooltips on with "Show NBT Tags" (more lines), or as it was. */
    private static void tooltips(boolean on) {
        Module module = ModuleManager.getInstance().getModule("EnhancedTooltips");
        if (module == null) return;
        if (on) {
            tooltipsWere = module.isEnabled();
            modifiedWere = module.getLastModified();
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save());
            module.setEnabled(true);
            if (module.getOption("Show NBT Tags") instanceof BoolOption nbt) nbt.set(true);
        } else if (!OPTIONS.isEmpty()) {
            OPTIONS.forEach(Option::load);
            OPTIONS.clear();
            module.setEnabled(tooltipsWere);
            module.setLastModified(modifiedWere);
        }
    }

    /** Slot, item id pairs ("" empty), as the client has them. */
    private static void expect(Driver d, String what, Object... pairs) {
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String have = d.item((Integer) pairs[i]);
            if (!have.equals(pairs[i + 1])) wrong.add("slot " + pairs[i] + " has '" + have + "', not '" + pairs[i + 1] + "'");
        }
        check(wrong.isEmpty(), what + (wrong.isEmpty() ? "" : " " + wrong));
    }

    /** A failed check is logged and counted; the sequence goes on (a driver exception skips to the clean-up). */
    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILURES.add(what);
            LOGGER.error("Lads items check FAIL: {}", what);
            return;
        }
        passed++;
        LOGGER.info("Lads items check PASS {}: {}", passed, what);
    }

    private static int shot(Driver d, String name) {
        d.shot(name);
        LOGGER.info("Lads items frame {}", name);
        return 10;
    }

    private static String file(String item) { return item.replace(':', '-'); }

    /** Lore line {@code index} of the tall tooltip, {@code length} characters ("Line 7: abcdef..."). */
    public static String loreLine(int index, int length) {
        StringBuilder line = new StringBuilder("Line " + (index + 1) + ": ");
        while (line.length() < length) line.append((char) ('a' + line.length() % 26));
        return line.toString();
    }
}
