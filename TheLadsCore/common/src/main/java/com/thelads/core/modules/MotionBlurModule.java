package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Motion Blur (Lunar, Badlion, Feather, OneClient): each frame keeps some of the last one (frame blending), so turning the camera
 * smears the picture. Strength 1-10 as Lunar's, default 5, the same blur at any frame rate (client.ScreenEffects.motionRetain).
 * The HUD stays sharp. Visual only; skipped while a shader pack renders the world.
 */
public final class MotionBlurModule extends Module {
    public static final String NAME = "MotionBlur";

    public final SliderOption strength = addOption(new SliderOption("Strength", 5, 1, 10, 1));

    public MotionBlurModule() {
        super(NAME, "Blends each frame with the last one, so camera turns blur. Strength 1-10.");
    }
}
