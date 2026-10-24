package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Glint Colorizer (Lunar, Badlion's Enchant Glint, OneClient's GlintColorizer) with Shiny Potions: the enchantment glint in your
 * colour (its alpha is the brightness) and speed, on items and armour. 1.8.9 draws the glint's grey texture in a vertex colour
 * ({@link #VANILLA}), which the adapter replaces; 1.20.1 and 26.x draw a purple texture, which the adapter multiplies by
 * colour / {@link #VANILLA} per channel (the texture's channels keep one ratio), so the same colour looks alike on every version and
 * the default is Minecraft's own glint. Shiny Potions: potions with effects glint, as up to 1.19.3 (1.8.9 always does; off, it stops).
 */
public final class GlintColorizerModule extends Module {
    public static final String NAME = "Glint Colorizer";
    /** Minecraft 1.8.9's glint colour (RenderItem.renderEffect's -8372020). */
    public static final int VANILLA = 0xFF8040CC;
    /** 1.8.9 armour draws the glint at 76% of the item colour (LayerArmorBase.renderGlint). */
    public static final float ARMOUR = 0.76F;

    public final ColorOption color = addOption(new ColorOption("Color", false, VANILLA));
    public final SliderOption speed = addOption(new SliderOption("Speed", 100, 0, 500, 10).percent());
    public final BoolOption shinyPotions = addOption(new BoolOption("Shiny Potions", true));

    /** Cached each tick: on, the vertex colour (1.8.9), the colour modulator (1.20.1/26.x), the speed factor and Shiny Potions. */
    public boolean on, shiny;
    public int vertexColor = VANILLA;
    public float red = 1, green = 1, blue = 1, speedFactor = 1;

    public GlintColorizerModule() {
        super(NAME, "The enchantment glint in your colour (alpha is brightness) and speed. Shiny Potions: potions with effects glint.");
    }

    public void refresh() {
        on = isEnabled();
        int argb = PvpVisuals.argb(color);
        vertexColor = vertexColor(argb);
        red = modulator(argb, 16);
        green = modulator(argb, 8);
        blue = modulator(argb, 0);
        speedFactor = (float) speed.getValue() / 100;
        shiny = shinyPotions.get();
    }

    /** 1.8.9's vertex colour: the colour darkened by its alpha (the glint is added to the item, its own alpha does not count). */
    static int vertexColor(int argb) {
        float a = (argb >>> 24) / 255F;
        return 0xFF000000 | Math.round((argb >> 16 & 0xFF) * a) << 16 | Math.round((argb >> 8 & 0xFF) * a) << 8 | Math.round((argb & 0xFF) * a);
    }

    /** One channel (bit shift 16, 8 or 0) of the modern colour modulator: 1 for Minecraft's own glint. */
    static float modulator(int argb, int shift) {
        return (argb >> shift & 0xFF) * ((argb >>> 24) / 255F) / (VANILLA >> shift & 0xFF);
    }
}
