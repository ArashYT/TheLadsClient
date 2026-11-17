package com.thelads.core.client.ladsapi;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.StringOption;
import java.util.List;
import java.util.function.Supplier;

/**
 * Settings a LadsClientAPI server may set or limit that are no module's options: the Lads HUD's global look ("HUD") and vanilla's
 * own options ("Minecraft"). A policy names them like a module's options, so the wire format stays as it is and a client that does
 * not know them reports them as ignored. The two modules are in no list, screen or config file and their options hold no value:
 * the real getter passes the player's own value in and gets the effective one back, so a server value is never saved.
 */
public final class ServerSettings {
    public static final String HUD = "HUD", MINECRAFT = "Minecraft";
    private static final Module HUD_MODULE = new Module(HUD, "The Lads HUD's global look.");
    private static final Module MINECRAFT_MODULE = new Module(MINECRAFT, "Minecraft's own options.");
    /** Set by a version that hooks vanilla's option getters; without it "Minecraft" rules are reported as ignored. */
    static volatile boolean vanillaHooked;
    /** Every other vanilla option and keybind by its options.txt name ("renderClouds", "key_key.jump"), listed once the game's options exist. */
    private static volatile Supplier<List<Option>> vanillaCatalog;
    private static boolean catalogued;
    /** The version's reaction to new rules (rerun option callbacks, rebind keys); any thread may call it. */
    private static volatile Runnable vanillaChanged;

    static {
        HUD_MODULE.addOption(new SliderOption("Scale", 0, 0, 16, 1)); // 0 follows vanilla's GUI scale
        HUD_MODULE.addOption(new BoolOption("Text Shadow", true));
        HUD_MODULE.addOption(new BoolOption("Soft Shadow", false));
        HUD_MODULE.addOption(new BoolOption("Backgrounds", true));
        HUD_MODULE.addOption(new SliderOption("FPS Limit", 60, 0, 240, 1)); // 0 = unlimited
        MINECRAFT_MODULE.addOption(new SliderOption("FOV", 70, 30, 110, 1));
        MINECRAFT_MODULE.addOption(new SliderOption("Brightness", 50, 0, 100, 1).suffix("%"));
        MINECRAFT_MODULE.addOption(new SliderOption("Render Distance", 12, 2, 32, 1));
        MINECRAFT_MODULE.addOption(new BoolOption("View Bobbing", true));
        MINECRAFT_MODULE.addOption(new BoolOption("Entity Shadows", true));
    }

    private ServerSettings() {}

    /** A keybind ("key_key.jump"): its rule's value is a key name as options.txt writes it ("key.keyboard.f", "key.mouse.left"). */
    public static final class KeyOption extends StringOption {
        public KeyOption(String name, String own) {
            super(name, own);
        }

        @Override
        public ServerControl serverControl() {
            return ServerControl.FULL;
        }

        static boolean valid(String key) {
            return key.length() <= 64 && (key.startsWith("key.keyboard.") || key.startsWith("key.mouse.") || key.startsWith("scancode."));
        }
    }

    /** A version calls this once its vanilla option getters ask {@link #vanilla}. */
    public static void vanillaHooked() {
        vanillaHooked = true;
    }

    /** Like {@link #vanillaHooked()}, and every option and keybind the catalog lists may be named too; onChange runs when the rules change. */
    public static void vanillaHooked(Supplier<List<Option>> catalog, Runnable onChange) {
        vanillaCatalog = catalog;
        vanillaChanged = onChange;
        vanillaHooked = true;
    }

    /** Called by ServerPolicy.set. */
    static void policyChanged() {
        Runnable r = vanillaChanged;
        if (r != null) {
            try {
                r.run();
            } catch (RuntimeException ignored) {}
        }
    }

    /** Adds the catalog's options to "Minecraft" the first time a policy names it (the game's options exist by then). */
    private static synchronized void catalog() {
        Supplier<List<Option>> catalog = vanillaCatalog;
        if (catalogued || catalog == null) return;
        List<Option> options;
        try {
            options = catalog.get();
        } catch (RuntimeException e) {
            return;
        }
        if (options == null) return;
        catalogued = true;
        for (Option option : options) {
            if (MINECRAFT_MODULE.getOption(option.getName()) == null) MINECRAFT_MODULE.addOption(option);
        }
    }

    /** The module a policy's "options" may name besides the registered ones, or null. */
    static Module module(String name) {
        if (HUD.equalsIgnoreCase(name)) return HUD_MODULE;
        if (MINECRAFT.equalsIgnoreCase(name) && vanillaHooked) {
            catalog();
            return MINECRAFT_MODULE;
        }
        return null;
    }

    static List<Module> modules() {
        return List.of(HUD_MODULE, MINECRAFT_MODULE);
    }

    public static boolean hud(String option, boolean own) {
        return ServerPolicy.applyBool(HUD, option, own);
    }

    public static int hud(String option, int own) {
        return (int) Math.round(slider(HUD_MODULE, option, own));
    }

    /** Whether the server has a rule on any vanilla option: all a vanilla option getter pays for on other servers. */
    public static boolean hasVanillaRules() {
        ServerPolicy policy = ServerPolicy.active();
        return policy != null && policy.hasOptionRules(MINECRAFT);
    }

    /** Whether this vanilla option (options.txt name) has a rule. */
    public static boolean hasVanillaRule(String option) {
        ServerPolicy policy = ServerPolicy.active();
        return policy != null && policy.option(MINECRAFT, option) != null;
    }

    /** A catalogued choice option: the index of the effective choice. */
    public static int vanillaChoice(String option, int ownIndex) {
        if (!(MINECRAFT_MODULE.getOption(option) instanceof DropdownOption d)) return ownIndex;
        return ServerPolicy.applyDropdown(MINECRAFT, option, ownIndex, d.getChoices());
    }

    /** A keybind's forced key ("key.keyboard.f"), or null to keep the player's own. */
    public static String vanillaKey(String option) {
        ServerPolicy policy = ServerPolicy.active();
        OptionRule rule = policy == null ? null : policy.option(MINECRAFT, option);
        if (rule == null || rule.choice == null || policy.refusesChanges()) return null;
        return rule.choice;
    }

    public static boolean vanilla(String option, boolean own) {
        return ServerPolicy.applyBool(MINECRAFT, option, own);
    }

    /** Brightness is a percentage (vanilla's 0.0 to 1.0 times 100). */
    public static double vanilla(String option, double own) {
        return slider(MINECRAFT_MODULE, option, own);
    }

    /** The own value untouched unless this option has a rule (no clamping to the range below without one). */
    private static double slider(Module module, String option, double own) {
        ServerPolicy policy = ServerPolicy.active();
        if (policy == null || policy.option(module.getName(), option) == null) return own;
        SliderOption slider = (SliderOption) module.getOption(option);
        return ServerPolicy.applySlider(module.getName(), option, own, slider.getMin(), slider.getMax(), slider.getStep());
    }
}
