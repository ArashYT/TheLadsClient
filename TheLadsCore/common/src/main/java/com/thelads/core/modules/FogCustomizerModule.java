package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Fog Customizer (Lunar/Badlion Fog Customizer, Feather Custom Fog): the distance fog moves (Fog Start and Fog End scale the game's
 * own distances) or goes (Disable Fog). Clearing the fog of water, of lava and powder snow, or of Blindness and Darkness, shows what
 * vanilla hides, so those three follow the server rules ({@link #CLEAR_VIEW} is in HypixelSafetyManager's risky
 * set: off on servers that ban it and where the rules are unknown). Each version sorts the fog it sets up into a {@link #medium} and
 * asks {@link #clears}, {@link #start} and {@link #end}.
 */
public final class FogCustomizerModule extends Module {
    public static final String NAME = "Fog Customizer";
    /** The server-rules name of the two see-through options. */
    public static final String CLEAR_VIEW = "FogClearView";
    /** What the camera's fog comes from. */
    public static final int TERRAIN = 0, WATER = 1, LAVA = 2, EFFECT = 3;
    /** A fog distance past anything the game draws: no fog. */
    public static final float FAR = 1.0e7f;
    private static volatile FogCustomizerModule cached;

    public final BoolOption disable, clearWater, clearLava, clearEffects;
    public final SliderOption fogStart, fogEnd;

    public FogCustomizerModule() {
        super(NAME, "Move or remove the distance fog and clear the fog under water. Clear Lava and Clear Blindness only work in "
            + "singleplayer, LAN and servers known to allow them.");
        disable = addOption(new BoolOption("Disable Fog", false));
        fogStart = addOption(new SliderOption("Fog Start", 100, 0, 200, 5).percent());
        fogEnd = addOption(new SliderOption("Fog End", 100, 10, 200, 5).percent());
        clearWater = addOption(new BoolOption("Clear Water", false));
        clearLava = addOption(new BoolOption("Clear Lava & Powder Snow", false));
        clearEffects = addOption(new BoolOption("Clear Blindness & Darkness", false));
    }

    public static FogCustomizerModule get() {
        FogCustomizerModule module = cached;
        if (module == null && ModuleManager.getInstance().getModule(NAME) instanceof FogCustomizerModule m) cached = module = m;
        return module;
    }

    /** The medium in vanilla's order: lava (and powder snow) first, then Blindness/Darkness, then water, else the distance fog. */
    public static int medium(boolean lavaOrPowderSnow, boolean blindOrDark, boolean water) {
        return lavaOrPowderSnow ? LAVA : blindOrDark ? EFFECT : water ? WATER : TERRAIN;
    }

    /** This medium's fog goes entirely ({@link #FAR}); see-through fog only where the server rules allow it. */
    public boolean clears(int medium) {
        if (!isEnabled()) return false;
        switch (medium) {
            case TERRAIN: return disable.get();
            case WATER: return clearWater.get() && !HypixelSafetyManager.isDisallowed(CLEAR_VIEW);
            case LAVA: return clearLava.get() && !HypixelSafetyManager.isDisallowed(CLEAR_VIEW);
            case EFFECT: return clearEffects.get() && !HypixelSafetyManager.isDisallowed(CLEAR_VIEW);
            default: return false;
        }
    }

    /** The distance fog's start: the game's scaled by Fog Start. */
    public float start(float vanilla) {
        return isEnabled() ? scaled(vanilla, fogStart.getValue()) : vanilla;
    }

    /** The distance fog's end: the game's scaled by Fog End, kept past {@code start} so the fog never inverts. */
    public float end(float vanilla, float start) {
        return isEnabled() ? Math.max(scaled(vanilla, fogEnd.getValue()), start + 0.1f) : vanilla;
    }

    static float scaled(float vanilla, double percent) {
        return (float) (vanilla * percent / 100);
    }
}
