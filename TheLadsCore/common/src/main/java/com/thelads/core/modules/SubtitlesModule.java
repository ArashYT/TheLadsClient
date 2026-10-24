package com.thelads.core.modules;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Subtitles (Lunar's Audio Subtitles, OneClient's Closed Captions, Feather's Subtitles): vanilla's subtitles moved, scaled and in your
 * text and background colours. Visual only; Minecraft 1.8.9 has no subtitles. The adapters read what {@link #refresh} cached.
 */
public final class SubtitlesModule extends Module {
    public static final String NAME = "Subtitles";
    /** Vanilla's subtitle box: black at 80% (Options.getBackgroundColor(0.8)). */
    public static final int VANILLA_BACKGROUND = 0xCC000000;

    public final SliderOption scale = addOption(new SliderOption("Scale", 100, 50, 200, 5).percent());
    public final SliderOption x = addOption(new SliderOption("X Offset", 0, -500, 500, 1));
    public final SliderOption y = addOption(new SliderOption("Y Offset", 0, -500, 500, 1));
    public final ColorOption text = addOption(new ColorOption("Text Color", false, 0xFFFFFFFF));
    public final ColorOption background = addOption(new ColorOption("Background", false, VANILLA_BACKGROUND));

    /** Cached each tick: on, the scale, the offset in GUI pixels, the text RGB and the background ARGB. */
    public boolean on;
    public float size = 1;
    public int dx, dy, textRgb = 0xFFFFFF, backgroundArgb = VANILLA_BACKGROUND;

    public SubtitlesModule() {
        super(NAME, "Move, scale and recolour the subtitles (closed captions).");
    }

    public void refresh() {
        on = isEnabled();
        size = (float) scale.getValue() / 100;
        dx = x.getIntValue();
        dy = y.getIntValue();
        textRgb = PvpVisuals.argb(text) & 0xFFFFFF;
        backgroundArgb = PvpVisuals.argb(background);
    }

    /** A subtitle's text colour: the chosen RGB dimmed as vanilla dims its white while the subtitle fades (brightness 0-255). */
    public static int fade(int rgb, int brightness) {
        int r = (rgb >> 16 & 0xFF) * brightness / 255, g = (rgb >> 8 & 0xFF) * brightness / 255, b = (rgb & 0xFF) * brightness / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
