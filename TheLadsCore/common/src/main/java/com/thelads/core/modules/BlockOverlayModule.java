package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Block Overlay (Lunar's Block Outline, Badlion's and Feather's Block Overlay): the outline of the block you look at in your colour
 * and line width, and a fill over the block. Visual only. The adapters read what {@link #refresh} cached.
 */
public final class BlockOverlayModule extends Module {
    public static final String NAME = "Block Overlay";
    /** Minecraft's own outline: black at 40%. */
    public static final int VANILLA_OUTLINE = 0x66000000;

    public final BoolOption outline = addOption(new BoolOption("Outline", true));
    public final ColorOption outlineColor = addOption(new ColorOption("Outline Color", false, VANILLA_OUTLINE));
    public final SliderOption width = addOption(new SliderOption("Line Width", 2, 0.5, 10, 0.5));
    public final BoolOption fill = addOption(new BoolOption("Fill", false));
    public final ColorOption fillColor = addOption(new ColorOption("Fill Color", false, 0x33FFFFFF));

    /** Cached each tick: on, the outline's and the fill's ARGB (0 when that part is off) and the line width in pixels. */
    public boolean on;
    public int outlineArgb, fillArgb;
    public float lineWidth = 2;

    public BlockOverlayModule() {
        super(NAME, "The outline of the block you look at in your colour and line width, with an optional fill.");
    }

    public void refresh() {
        on = isEnabled();
        outlineArgb = outline.get() ? PvpVisuals.argb(outlineColor) : 0;
        fillArgb = fill.get() ? PvpVisuals.argb(fillColor) : 0;
        lineWidth = (float) width.getValue();
    }
}
