package com.thelads.core.modules;

import com.thelads.core.client.RenderScalePolicy;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;

/**
 * Better Resolution (1.6.0's RenderScale, whose saved settings ConfigManager carries over): the world renders at its own
 * resolution and is rebuilt at native size by the adapters' composite (Smooth and Sharp: shaders/include/world_upscale.glsl);
 * the HUD and menus always render at native resolution. Option names and choice order are 1.6.0's, so saved values keep meaning.
 */
public class BetterResolutionModule extends Module {
    public static final String NAME = "BetterResolution";
    private static final int[] TARGET_FPS = {30, 60, 90, 120, 144, 0};
    public final DropdownOption preset = addOption(new DropdownOption("Preset", 0, "Custom", "Ultra Performance", "Balanced", "Quality", "Super Sampling"));
    public final SliderOption scale = addOption(new SliderOption("Scale", 100, 50, 200, 5));
    public final DropdownOption method = addOption(new DropdownOption("Algorithm", RenderScalePolicy.SMOOTH, "Linear", "Nearest", "Smooth", "Sharp"));
    public final BoolOption dynamic = addOption(new BoolOption("Dynamic Resolution", false));
    public final DropdownOption targetFps = addOption(new DropdownOption("Target FPS", 1, "30", "60", "90", "120", "144", "Unlimited"));
    public final SliderOption minimum = addOption(new SliderOption("Min Scale", 50, 50, 100, 25));

    public BetterResolutionModule() {
        super(NAME, "Render the world below (or above) native resolution and rebuild it with Smooth or Sharp upscaling. "
            + "The HUD and menus stay sharp. Scale applies with the Custom preset.");
        setEnabled(true); // at 100 % (the default) it renders exactly as without it
    }

    /** The manual Scale slider only counts with the Custom preset; the menu greys it out otherwise. */
    public boolean locked(Option option) { return option == scale && preset.getIndex() != 0; }

    /** The current settings, off while an external Better Resolution jar is loaded (it would scale the world a second time). */
    public RenderScalePolicy.Settings settings(boolean externalLoaded) {
        return new RenderScalePolicy.Settings(isEnabled() && !externalLoaded, preset.getIndex(), scale.getValue(), method.getIndex(),
            dynamic.get(), TARGET_FPS[targetFps.getIndex()], minimum.getValue());
    }
}
