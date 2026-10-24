package com.thelads.core.client;

import com.thelads.core.modules.InventoryMoveModule;

/**
 * Inventory move helper: checks if player movement is permitted while an inventory menu is open.
 */
public final class InventoryMove {
    private InventoryMove() {}

    public static boolean isMovementAllowed() {
        return InventoryMoveModule.isAllowed();
    }
}
