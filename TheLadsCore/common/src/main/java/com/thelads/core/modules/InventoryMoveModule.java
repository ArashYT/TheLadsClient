package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Inventory move: allows walking and jumping while container GUIs remain open.
 * GATED: movement during open inventories triggers anticheat flags; blocked on unknown servers.
 */
public final class InventoryMoveModule extends Module {
    public static final String NAME = "InventoryMove";

    public InventoryMoveModule() {
        super(NAME, "Allows walking and navigating while inventory and chest menus remain open. "
                + "Gated: off on servers where GUI movement is risky or rules are unknown.");
        setEnabled(false); // Gated modules default OFF
    }

    public static boolean isAllowed() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod != null && mod.isEnabled() && !HypixelSafetyManager.isDisallowed(NAME);
    }
}
