package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Container Sorting (gated, default OFF): sorts container contents and player inventory
 * via simulated window click packets. Gated by HypixelSafetyManager under anti-ban rules.
 */
public class ContainerSortingModule extends Module {
    public static final String NAME = "Container Sorting";

    public final BoolOption sortContainer;
    public final BoolOption sortInventory;

    public ContainerSortingModule() {
        super(NAME, "Client-side container and inventory sorting via simulated click packets.");
        sortContainer = addOption(new BoolOption("Sort container contents", true));
        sortInventory = addOption(new BoolOption("Sort player inventory", true));
        setEnabled(false);
    }
}
