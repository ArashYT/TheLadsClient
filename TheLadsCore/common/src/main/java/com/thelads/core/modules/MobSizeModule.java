package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Mob Size (Lunar): hostile and passive mobs are drawn bigger or smaller, on this client only. The scale is applied inside the
 * mob's own model transform (about its feet), so nothing else in the frame moves. Their hit boxes stay the server's, so a resized
 * mob misleads where to hit: a risky module (HypixelSafetyManager), off by default. Players are never resized.
 */
public final class MobSizeModule extends Module {
    public static final String NAME = "Mob Size";
    private static boolean active;
    private static float hostile = 1, passive = 1;

    public final SliderOption hostileSize = addOption(new SliderOption("Hostile size", 100, 25, 300, 5).percent());
    public final SliderOption passiveSize = addOption(new SliderOption("Passive size", 100, 25, 300, 5).percent());

    public MobSizeModule() {
        super(NAME, "Draw hostile and passive mobs bigger or smaller on your screen. Hit boxes stay the same.");
    }

    /** Reads the settings into fields (each version's client tick). */
    public static void refresh() {
        MobSizeModule module = ModuleManager.getInstance().getModule(NAME) instanceof MobSizeModule m ? m : null;
        active = module != null && module.isEnabled();
        if (!active) return;
        hostile = (float) module.hostileSize.getValue() / 100f;
        passive = (float) module.passiveSize.getValue() / 100f;
    }

    /** The render scale of a mob (1 = vanilla); {@code hostile} for monsters, else a passive mob. */
    public static float scale(boolean hostileMob) {
        return !active ? 1 : hostileMob ? hostile : passive;
    }
}
