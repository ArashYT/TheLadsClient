package com.thelads.core.client.ladsapi;

/**
 * The current server's rules: one immutable snapshot, or none. Read by Module/Option getters every frame, so lookups never allocate.
 * Only ever in memory: config files keep the player's own values, so leaving, a crash or a server switch restores them.
 *
 * FROZEN INTERFACE (phase 0): option hooks (lane HOOKS), GUI (lane GUI) and HypixelSafetyManager call exactly these.
 * Lane CORE builds snapshots from policy messages (and implements the bodies marked below).
 */
public final class ServerPolicy {
    private static volatile ServerPolicy active;

    private ServerPolicy() {}

    /** The rules in force, or null (no LadsClientAPI server, or not yet received). One volatile read: the fast path for every getter. */
    public static ServerPolicy active() {
        return active;
    }

    /** Lane CORE: installs a validated snapshot (null clears). */
    static void set(ServerPolicy policy) {
        active = policy;
    }

    /** The server's display name from its hello ("Lads SMP"). */
    public String serverName() {
        return ""; // lane CORE
    }

    /** The module's state; INHERIT when not mentioned (after applying "unlisted"). Exact registered module name. */
    public ModuleState module(String moduleName) {
        return ModuleState.INHERIT; // lane CORE
    }

    /** The option's rule, or null. Exact registered module and option names. */
    public OptionRule option(String moduleName, String optionName) {
        return null; // lane CORE
    }

    /** A forced change the player turned off ("Let servers change my Lads settings"): its module is "on" or its rule forces a value. */
    public boolean refusesChanges() {
        return false; // lane CORE: reads the player's setting
    }

    // ---- helpers for the option getters (lane HOOKS calls these; lane CORE implements). Each returns `own` when no rule applies. ----

    public static boolean applyBool(String module, String option, boolean own) {
        return own; // lane CORE
    }

    /** Clamps into the option's own [min, max] and step after applying the rule. */
    public static double applySlider(String module, String option, double own, double min, double max, double step) {
        return own; // lane CORE
    }

    /** Returns an index into {@code choices}. */
    public static int applyDropdown(String module, String option, int ownIndex, String[] choices) {
        return ownIndex; // lane CORE
    }

    public static int applyColor(String module, String option, int ownArgb) {
        return ownArgb; // lane CORE
    }
}
