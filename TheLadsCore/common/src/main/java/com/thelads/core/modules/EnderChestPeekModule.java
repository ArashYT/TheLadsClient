package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Ender Chest Peek: previews cached contents of your personal Ender Chest upon hovering over an Ender Chest in any inventory.
 * GATED: shows inventory info without opening container.
 */
public class EnderChestPeekModule extends Module {
    public static final String NAME = "Ender Chest Peek";

    public final BoolOption showEmptySlots;

    public EnderChestPeekModule() {
        super(NAME, "Previews contents of personal Ender Chest when hovering over an Ender Chest item.");
        showEmptySlots = addOption(new BoolOption("Show empty slots", true));
        setEnabled(false); // GATED: defaults off
    }
}
