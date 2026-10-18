package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * StackRefill: automatically refills a depleted hand stack or broken tool from matching stacks in inventory.
 * Singleplayer only, via HypixelSafetyManager (servers' anticheats ban its hidden inventory clicks).
 */
public final class StackRefillModule extends Module {
    public static final String NAME = "StackRefill";

    public final BoolOption refillConsumables;
    public final BoolOption replaceBrokenTools;
    public final BoolOption refillOffhand;

    private static volatile StackRefillModule cachedInstance;
    private boolean noOffhand;

    /** 1.8.9 has no offhand: the menu leaves out Refill offhand there. */
    public void noOffhand() {
        noOffhand = true;
    }

    public boolean hidden(com.thelads.core.config.Option option) {
        return noOffhand && option == refillOffhand;
    }

    public StackRefillModule() {
        super(NAME, "Automatically refills a depleted hand stack or broken tool from matching stacks in your inventory. "
                + "Singleplayer only: servers' anticheats ban the hidden inventory clicks.");
        refillConsumables = addOption(new BoolOption("Refill consumables & blocks", true));
        replaceBrokenTools = addOption(new BoolOption("Replace broken tools", true));
        refillOffhand = addOption(new BoolOption("Refill offhand", true));
        setEnabled(true);
    }

    public static StackRefillModule get() {
        StackRefillModule instance = cachedInstance;
        if (instance == null) {
            if (ModuleManager.getInstance().getModule(NAME) instanceof StackRefillModule m) {
                cachedInstance = instance = m;
            }
        }
        return instance;
    }

    public boolean canRefillConsumables() {
        return isEnabled() && refillConsumables.get();
    }

    public boolean canReplaceBrokenTools() {
        return isEnabled() && replaceBrokenTools.get();
    }

    public boolean canRefillOffhand() {
        return isEnabled() && refillOffhand.get();
    }
}
