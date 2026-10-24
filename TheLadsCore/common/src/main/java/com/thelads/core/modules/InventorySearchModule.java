package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Inventory search bar: adds an in-game search bar to container menus to highlight items.
 */
public final class InventorySearchModule extends Module {
    public static final String NAME = "InventorySearch";

    public InventorySearchModule() {
        super(NAME, "Adds an interactive search bar in inventory and container menus to highlight matching items.");
        addOption(new BoolOption("Search Tooltips", true));
        addOption(new BoolOption("Dim Non-Matching", true));
        setEnabled(false);
    }
}
