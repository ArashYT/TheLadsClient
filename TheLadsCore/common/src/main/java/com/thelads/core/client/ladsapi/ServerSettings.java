package com.thelads.core.client.ladsapi;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import java.util.List;

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

    static {
        HUD_MODULE.addOption(new SliderOption("Scale", 0, 0, 16, 1)); // 0 follows vanilla's GUI scale
        HUD_MODULE.addOption(new BoolOption("Text Shadow", true));
        HUD_MODULE.addOption(new BoolOption("Soft Shadow", false));
        HUD_MODULE.addOption(new BoolOption("Backgrounds", true));
        MINECRAFT_MODULE.addOption(new SliderOption("FOV", 70, 30, 110, 1));
        MINECRAFT_MODULE.addOption(new SliderOption("Brightness", 50, 0, 100, 1).suffix("%"));
        MINECRAFT_MODULE.addOption(new SliderOption("Render Distance", 12, 2, 32, 1));
        MINECRAFT_MODULE.addOption(new BoolOption("View Bobbing", true));
        MINECRAFT_MODULE.addOption(new BoolOption("Entity Shadows", true));
    }

    private ServerSettings() {}

    /** A version calls this once its vanilla option getters ask {@link #vanilla}. */
    public static void vanillaHooked() {
        vanillaHooked = true;
    }

    /** The module a policy's "options" may name besides the registered ones, or null. */
    static Module module(String name) {
        if (HUD.equalsIgnoreCase(name)) return HUD_MODULE;
        if (MINECRAFT.equalsIgnoreCase(name) && vanillaHooked) return MINECRAFT_MODULE;
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
