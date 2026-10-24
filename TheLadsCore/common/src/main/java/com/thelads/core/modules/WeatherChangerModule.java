package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;

/**
 * Weather Changer: this client draws clear weather, rain, a thunderstorm or snow whatever the server's weather is (nothing is sent;
 * the integrated server's weather is never touched: the adapters only change the client world's rain and thunder levels, and what
 * falls where the client draws precipitation).
 */
public final class WeatherChangerModule extends Module {
    public static final String NAME = "Weather Changer";
    /** The choices, in the dropdown's order. */
    public static final int CLEAR = 0, RAIN = 1, THUNDER = 2, SNOW = 3;
    /** What falls in a column: as 26.x/1.20.1 Biome.Precipitation (NONE, RAIN, SNOW). */
    public static final int FALLS_NONE = 0, FALLS_RAIN = 1, FALLS_SNOW = 2;
    private static volatile WeatherChangerModule cached;

    public final DropdownOption weather;

    public WeatherChangerModule() {
        super(NAME, "Choose the weather you see: clear, rain, thunder or snow (only on your screen; the server's weather is unchanged).");
        weather = addOption(new DropdownOption("Weather", CLEAR, "Clear", "Rain", "Thunder", "Snow"));
    }

    public static WeatherChangerModule get() {
        WeatherChangerModule module = cached;
        if (module == null && ModuleManager.getInstance().getModule(NAME) instanceof WeatherChangerModule m) cached = module = m;
        return module;
    }

    /** The weather to draw, or -1 while the module is off (the server's weather shows). */
    public int forced() {
        return isEnabled() ? weather.getIndex() : -1;
    }

    /** The client world's rain level (0 to 1) with this weather forced. */
    public static float rain(int forced) {
        return forced == CLEAR ? 0 : 1;
    }

    /** The client world's thunder level (0 to 1) with this weather forced. */
    public static float thunder(int forced) {
        return forced == THUNDER ? 1 : 0;
    }

    /**
     * What falls where the biome would let {@code biome} fall, with this weather forced (-1: the biome's own). Snow falls everywhere;
     * rain and thunder bring rain to every biome, dry ones included, but a snowy biome keeps its snow.
     */
    public static int precipitation(int forced, int biome) {
        if (forced == SNOW) return FALLS_SNOW;
        if (forced == RAIN || forced == THUNDER) return biome == FALLS_SNOW ? FALLS_SNOW : FALLS_RAIN;
        return biome;
    }
}
