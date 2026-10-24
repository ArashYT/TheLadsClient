package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Loot Beams (Feather): a beam of light rises from every dropped item, in the color of its rarity (common white, uncommon yellow,
 * rare aqua, epic purple). A beam stands far above the item, so it gives away loot behind walls: a risky module
 * (HypixelSafetyManager), off by default. Each version's item renderer draws {@link #height} blocks of beam after the item.
 */
public final class LootBeamsModule extends Module {
    public static final String NAME = "Loot Beams";
    /** Item rarity as the versions report it: 0 common, 1 uncommon, 2 rare, 3 epic. */
    private static final int[] RARITY = {0xFFFFFF, 0xFFFF55, 0x55FFFF, 0xFF55FF};
    private static boolean active, common;
    private static float height = 4, alpha = .5f;

    public final SliderOption beamHeight = addOption(new SliderOption("Beam height", 4, 1, 16, 1));
    public final SliderOption opacity = addOption(new SliderOption("Beam opacity", 50, 10, 100, 5).percent());
    public final BoolOption commonItems = addOption(new BoolOption("Common items", true));

    public LootBeamsModule() {
        super(NAME, "A beam of light rises from dropped items, colored by rarity (white, yellow, aqua, purple).");
    }

    /** Reads the settings into fields (each version's client tick). */
    public static void refresh() {
        LootBeamsModule module = ModuleManager.getInstance().getModule(NAME) instanceof LootBeamsModule m ? m : null;
        active = module != null && module.isEnabled();
        if (!active) return;
        height = (float) module.beamHeight.getValue();
        alpha = (float) module.opacity.getValue() / 100f;
        common = module.commonItems.get();
    }

    public static boolean active() { return active; }

    /** Whether an item of {@code rarity} gets a beam right now. */
    public static boolean beams(int rarity) { return active && (rarity > 0 || common); }
    public static float height() { return height; }
    public static float alpha() { return alpha; }

    /** The beam's RGB for a rarity (0 to 3; anything else is common). */
    public static int color(int rarity) { return RARITY[rarity >= 0 && rarity < RARITY.length ? rarity : 0]; }
}
