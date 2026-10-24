package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Inventory sort and middle-click armor swap.
 * GATED: rapid multi-clicks for sorting trigger fast-click/inv-move checks; blocked on unknown servers.
 */
public final class InventorySortModule extends Module {
    public static final String NAME = "InventorySort";
    private final BoolOption armorSwap;

    public InventorySortModule() {
        super(NAME, "Inventory sorting and middle-click armor swapping. "
                + "Gated: off on servers where automated clicks are risky or rules are unknown.");
        armorSwap = addOption(new BoolOption("Middle-click armor swap", true));
        addOption(new BoolOption("Sort Player Inventory", true));
        addOption(new BoolOption("Sort Chests", true));
        setEnabled(false); // Gated modules default OFF
    }

    public static boolean isAllowed() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod != null && mod.isEnabled() && !HypixelSafetyManager.isDisallowed(NAME);
    }

    public static boolean armorSwapEnabled() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        if (mod instanceof InventorySortModule ism) {
            return ism.armorSwap.get();
        }
        return false;
    }
}
