package com.thelads.core.modules;

import com.thelads.core.config.*;
import com.thelads.core.config.Module;

/**
 * Lunar, OneClient & Feather parity Subtitles / Closed Captions customizer.
 * Controls scale, positioning, text color, sound distance indicator, and background opacity.
 * Visual-only. 1.8.9 n/a.
 */
public class SubtitlesModule extends Module {
    public static final String NAME = "Subtitles";

    public SubtitlesModule() {
        super(NAME, "Move, scale and recolor closed-captions and sound subtitles.");
        addOption(new SliderOption("Scale", 100, 50, 200, 5));
        addOption(new SliderOption("X Offset", 0, -500, 500, 1));
        addOption(new SliderOption("Y Offset", 0, -500, 500, 1));
        addOption(new ColorOption("Text Color", true, 0xFFFFFFFF));
        addOption(new ColorOption("Background", true, 0x80000000));
        addOption(new BoolOption("Show Distance", true));
        addOption(new BoolOption("Text Shadow", true));
        setEnabled(false);
    }

    public static float getScale() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 1.0f;
        Option o = m.getOption("Scale");
        return (o instanceof SliderOption so) ? so.getIntValue() / 100.0f : 1.0f;
    }

    public static int getXOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("X Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }

    public static int getYOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("Y Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }

    public static boolean isShowDistance() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return true;
        Option o = m.getOption("Show Distance");
        return (o instanceof BoolOption bo) ? bo.get() : true;
    }
}
