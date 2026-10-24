package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Item Despawn (Feather): a dropped item blinks during its last seconds before it despawns (5 minutes after it dropped), faster as
 * the end nears. The age is the one this client counts from when it first saw the item (vanilla clients count it too); an item that
 * dropped before you came near blinks late. Each version's item renderer skips the item on the blink's off frames.
 */
public final class ItemDespawnModule extends Module {
    public static final String NAME = "Item Despawn";
    /** Vanilla's despawn age in ticks (5 minutes). */
    public static final int LIFETIME = 6000;
    private static boolean active;
    private static int warnTicks = 600;

    public final SliderOption warning = addOption(new SliderOption("Warning seconds", 30, 5, 120, 5));

    public ItemDespawnModule() {
        super(NAME, "Dropped items blink before they despawn, faster as the 5 minutes run out.");
    }

    /** Reads the settings into fields (each version's client tick). */
    public static void refresh() {
        ItemDespawnModule module = ModuleManager.getInstance().getModule(NAME) instanceof ItemDespawnModule m ? m : null;
        active = module != null && module.isEnabled();
        if (active) warnTicks = module.warning.getIntValue() * 20;
    }

    public static boolean active() { return active; }

    /** True on the blink's off frames: the item is not drawn. {@code age} in ticks, {@code lifetime} the item's despawn age. */
    public static boolean hidden(int age, int lifetime) {
        return active && hidden(age, lifetime, warnTicks);
    }

    /**
     * The blink: within {@code warn} ticks of {@code lifetime}, the item shows and hides for equal spans of 10 ticks, 5 in the last
     * 10 seconds and 2 in the last 3 seconds. Items that never despawn (age -32768 holds them) never blink.
     */
    static boolean hidden(int age, int lifetime, int warn) {
        int left = lifetime - age;
        if (age < 0 || left > warn || left <= 0) return false;
        int span = left > 200 ? 10 : left > 60 ? 5 : 2;
        return left / span % 2 == 1;
    }
}
