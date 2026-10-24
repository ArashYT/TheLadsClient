package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Ender Chest Peek (Badlion's Show Ender Chest Inventory, OneClient's ender chest peek): an ender chest item's tooltip shows what the
 * player's ender chest held when they last closed it. Only an ender chest screen the server opened for the player fills it (a screen
 * titled with vanilla's {@link #TITLE_KEY}), and leaving the server forgets it. Seeing those items away from the chest is more than
 * vanilla shows, so the module is gated (HypixelSafetyManager risky set). Shulker boxes are ShulkerBoxUtils' (gated the same way).
 */
public final class EnderChestPeek {
    public static final String NAME = "Ender Chest Peek";
    /** The translation key of the title servers give the ender chest screen. */
    public static final String TITLE_KEY = "container.enderchest";
    /** The module is on and the server rules allow it: cached once a client tick. */
    public static boolean on;
    private static Module module;

    private EnderChestPeek() {}

    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
    }
}
