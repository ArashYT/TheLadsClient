package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Hide Foliage (Lunar; Badlion's Betterframes foliage culling): grass, ferns, bushes, seagrass, nether roots and sprouts and leaf
 * litter are not drawn, by exact block id (client.HideFoliage), which also spares the chunk builder their quads. Ground cover lower
 * than a player is visual only. Tall Plants (tall grass, large ferns, tall seagrass, tall dry grass) can hide a crouching player,
 * so removing them shows what the vanilla client would not: it is gated by the server rules (HypixelSafetyManager, off on servers
 * that ban it or whose rules are unknown) and off by default.
 */
public final class HideFoliageModule extends Module {
    public static final String NAME = "HideFoliage";

    public final BoolOption grass = addOption(new BoolOption("Grass & Ferns", true));
    public final BoolOption bushes = addOption(new BoolOption("Bushes", true));
    public final BoolOption seagrass = addOption(new BoolOption("Seagrass", true));
    public final BoolOption nether = addOption(new BoolOption("Nether Plants", true));
    public final BoolOption leafLitter = addOption(new BoolOption("Leaf Litter", true));
    public final BoolOption tall = addOption(new BoolOption("Tall Plants", false));

    public HideFoliageModule() {
        super(NAME, "Stops drawing grass, ferns, bushes, seagrass, nether roots and leaf litter. Tall Plants follow server rules.");
    }
}
