package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Item Physics (common.client.ItemPhysics holds the shared sums). The look works on every server; the SP rules change how the
 * integrated server treats items, so they apply only in singleplayer and in worlds this client hosts: wood floats and stone sinks,
 * only flammable items burn (Fireproofing), burning items ignite flammable blocks, cactus spares items, the despawn time,
 * right-click pickup and the charged throw (hold the drop key).
 */
public final class ItemPhysicsModule extends Module {
    public static final String NAME = "Item Physics";

    public final BoolOption animation = addOption(new BoolOption("Item animation", true));
    public final BoolOption floating = addOption(new BoolOption("SP: Wood floats", true));
    public final BoolOption fireproof = addOption(new BoolOption("SP: Fireproofing", true));
    public final BoolOption ignite = addOption(new BoolOption("SP: Items ignite", true));
    public final BoolOption cactus = addOption(new BoolOption("SP: Cactus-safe", true));
    public final SliderOption despawn = addOption(new SliderOption("SP: Despawn mins", 5, 1, 30, 1));
    public final BoolOption pickup = addOption(new BoolOption("SP: Click pickup", false));
    public final BoolOption charged = addOption(new BoolOption("SP: Charged throw", true));

    public ItemPhysicsModule() {
        // The menu cuts option names at about 17 characters: "SP:" marks the singleplayer rules, as the description says.
        super(NAME, "Items lie flat, tumble and float. Options marked SP work only in singleplayer and worlds you host.");
    }
}
