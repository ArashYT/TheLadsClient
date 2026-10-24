package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * UHC Overlay (Feather): golden apples, heads and ores (with their ingots and gems) are drawn bigger in the hotbar and in every
 * inventory, so they stand out. Only the item's picture grows, about its slot's center; counts and durability bars stay. Each
 * version's GUI item renderer asks {@link #scale} with the item's group.
 */
public final class UhcOverlayModule extends Module {
    public static final String NAME = "UHC Overlay";
    /** An item's group, as a version reports it. */
    public static final int NONE = 0, APPLE = 1, HEAD = 2, ORE = 3;
    private static boolean active;
    private static final boolean[] ON = new boolean[4];
    private static float scale = 1.5f;

    public final SliderOption size = addOption(new SliderOption("Item scale", 150, 110, 200, 5).percent());
    public final BoolOption apples = addOption(new BoolOption("Golden apples", true));
    public final BoolOption heads = addOption(new BoolOption("Heads", true));
    public final BoolOption ores = addOption(new BoolOption("Ores and ingots", true));

    public UhcOverlayModule() {
        super(NAME, "Golden apples, heads and ores are drawn bigger in your hotbar and inventories so they stand out.");
    }

    /** Reads the settings into fields (each version's client tick). */
    public static void refresh() {
        UhcOverlayModule module = ModuleManager.getInstance().getModule(NAME) instanceof UhcOverlayModule m ? m : null;
        active = module != null && module.isEnabled();
        if (!active) return;
        scale = (float) module.size.getValue() / 100f;
        ON[APPLE] = module.apples.get(); ON[HEAD] = module.heads.get(); ON[ORE] = module.ores.get();
    }

    public static boolean active() { return active; }

    /** The icon scale for an item of {@code group} (1 = vanilla). */
    public static float scale(int group) {
        return active && group != NONE && ON[group] ? scale : 1;
    }
}
