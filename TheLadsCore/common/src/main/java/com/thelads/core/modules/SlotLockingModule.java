package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Slot locking: prevents dropping items from locked inventory slots (Alt+Click / L to lock).
 */
public final class SlotLockingModule extends Module {
    public static final String NAME = "SlotLocking";

    public SlotLockingModule() {
        super(NAME, "Locks selected inventory slots to prevent accidental dropping or transferring.");
        addOption(new BoolOption("Prevent Shift-Click", true));
        addOption(new BoolOption("Show Lock Icon", true));
        setEnabled(false);
    }
}
