package com.thelads.core.client;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.TextOption;
import java.util.Locale;

/**
 * The inventory modules of the parity-1023 chat lane, shared by every version (each adapter calls {@link #tick} once a client tick):
 * Slot Lock (the player's own drops and clicks that would move a locked item are cancelled on the client; nothing is sent), Inventory
 * Search (a search box on container screens dims the stacks that do not match), and the two gated ones, which HypixelSafetyManager
 * turns off on servers that ban them or whose rules are unknown: Inventory Move (walk and jump with a container screen open, which a
 * server sees as moving while a screen is open) and Armor Swap (middle-click armor in the player's own inventory screen to wear it:
 * two or three ordinary slot clicks). Slot ids here are the player's inventory indexes: hotbar 0-8, main 9-35, armor 36-39 and
 * offhand 40, never a container's own slots.
 */
public final class InventoryTweaks {
    public static final String SLOT_LOCK = "Slot Lock", LOCK_KEY = "Lock key", LOCKED = "Locked Slots", SEARCH = "Inventory Search",
        MOVE = "Inventory Move", ARMOR_SWAP = "Armor Swap";
    public static final String[] MODULES = {SLOT_LOCK, SEARCH, MOVE, ARMOR_SWAP};
    /** Vanilla's click types in order (1.8.9 modes, ClickType and ContainerInput ordinals). */
    public static final int PICKUP = 0, QUICK_MOVE = 1, SWAP = 2, CLONE = 3, THROW = 4, QUICK_CRAFT = 5, PICKUP_ALL = 6;
    public static final int SLOTS = 41, OFFHAND = 40;
    /** Overlays: a locked slot's tint, and the dim over stacks Inventory Search does not match. */
    public static final int LOCK_TINT = 0x50FF3030, LOCK_EDGE = 0xC0FF5050, DIM = 0xC0101010;
    public static final int SEARCH_WIDTH = 90, SEARCH_HEIGHT = 12;

    public static boolean lockOn, searchOn, moveOn, armorSwapOn;
    private static final boolean[] LOCKED_SLOTS = new boolean[SLOTS];
    private static String lockedSource;
    private static String query = "";

    private InventoryTweaks() {}

    /** Once a client tick, on the client thread. */
    public static void tick() {
        ModuleManager modules = ModuleManager.getInstance();
        Module lock = modules.getModule(SLOT_LOCK);
        lockOn = on(lock);
        searchOn = on(modules.getModule(SEARCH));
        moveOn = on(modules.getModule(MOVE));
        armorSwapOn = on(modules.getModule(ARMOR_SWAP));
        if (lock != null && lock.getOption(LOCKED) instanceof TextOption text && text.getValue() != lockedSource) parse(lockedSource = text.getValue());
        if (!searchOn) query = "";
    }

    private static boolean on(Module module) {
        return module != null && module.isEnabled();
    }

    // Slot Lock

    static void parse(String source) {
        java.util.Arrays.fill(LOCKED_SLOTS, false);
        for (String part : source.split(",")) {
            try {
                int slot = Integer.parseInt(part.trim());
                if (slot >= 0 && slot < SLOTS) LOCKED_SLOTS[slot] = true;
            } catch (NumberFormatException ignored) {
                // a typo in the list locks nothing
            }
        }
    }

    /** The player's inventory slot is locked (Slot Lock on). */
    public static boolean locked(int slot) {
        return lockOn && slot >= 0 && slot < SLOTS && LOCKED_SLOTS[slot];
    }

    /** The Lock key over one of the player's own slots: locks or unlocks it, and saves the list. */
    public static void toggleLock(int slot) {
        if (!lockOn || slot < 0 || slot >= SLOTS) return;
        LOCKED_SLOTS[slot] = !LOCKED_SLOTS[slot];
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) if (LOCKED_SLOTS[i]) list.append(list.length() == 0 ? "" : ",").append(i);
        if (ModuleManager.getInstance().getModule(SLOT_LOCK).getOption(LOCKED) instanceof TextOption text) {
            text.setValue(list.toString());
            lockedSource = text.getValue();
        }
        ConfigManager.saveLater();
    }

    /**
     * Slot Lock: a click the player must not make because it would move a locked item. {@code slot} is the clicked slot's player
     * inventory index (-1 for a container's own slot), {@code type} one of the click types, {@code button} the hotbar index of a
     * number-key swap (40 = offhand). Picking up all of a kind (double click) is checked by the adapters, which see the stacks.
     */
    public static boolean blocks(int slot, int type, int button) {
        if (!lockOn || type == CLONE || type == QUICK_CRAFT) return false;
        if (locked(slot)) return true;
        return type == SWAP && (button >= 0 && button < 9 || button == OFFHAND) && locked(button);
    }

    // Inventory Search

    public static String query() { return query; }

    public static void setQuery(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /** An item (its shown name and its id, e.g. "minecraft:diamond_sword") matches the search; any item while it is empty. */
    public static boolean matches(String name, String id) {
        if (query.isEmpty()) return true;
        return name.toLowerCase(Locale.ROOT).contains(query) || id != null && id.toLowerCase(Locale.ROOT).contains(query);
    }

    // Armor Swap

    /** The player inventory screen's slot (vanilla's InventoryMenu/ContainerPlayer: 5 head .. 8 feet) for an armor type 0 head .. 3 feet. */
    public static int armorMenuSlot(int type) {
        return type >= 0 && type <= 3 ? 5 + type : -1;
    }
}
