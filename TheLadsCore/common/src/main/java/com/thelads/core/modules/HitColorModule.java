package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;

/**
 * Hit Color (Lunar, Badlion, OneClient's DamageTint): the colour and strength of the red flash a hurt or dying mob or player shows,
 * its armour too with 1.7 Animations' red armour. The colour's alpha is the strength: Minecraft mixes 30% red into the model.
 * 1.20.1 and 26.x keep the flash in one row of the entity overlay texture, which the adapter repaints when the colour changes;
 * 1.8.9 sets it per entity in RendererLivingEntity.setBrightness.
 */
public final class HitColorModule extends Module {
    public static final String NAME = "Hit Color";
    /** Minecraft's flash: red at 30%. */
    public static final int VANILLA = 0x4DFF0000;

    public final ColorOption color = addOption(new ColorOption("Color", false, VANILLA));

    /** Cached each tick: on, and the flash's ARGB. Off, each adapter leaves its version's own flash. */
    public boolean on;
    public int argb = VANILLA;

    public HitColorModule() {
        super(NAME, "The colour and strength (alpha) of the flash hurt mobs and players show.");
    }

    public void refresh() {
        on = isEnabled();
        argb = PvpVisuals.argb(color);
    }

    /**
     * The overlay texture's hurt pixel (ARGB) for a flash: the shader keeps the model's colour by the pixel's alpha, so the flash's
     * strength becomes its complement.
     */
    public static int overlayPixel(int argb) {
        return (255 - (argb >>> 24)) << 24 | argb & 0xFFFFFF;
    }

    /** ARGB in NativeImage's ABGR order (1.20.1 setPixelRGBA). */
    public static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }
}
