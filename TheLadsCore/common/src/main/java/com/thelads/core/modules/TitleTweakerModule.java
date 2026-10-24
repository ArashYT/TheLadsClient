package com.thelads.core.modules;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.*;
import com.thelads.core.config.Module;

/**
 * Lunar, OneClient & Feather parity Title Tweaker.
 * Moves in-game titles, adjusts scaling, or routes title/subtitle text directly to chat.
 * Visual-only.
 */
public class TitleTweakerModule extends Module {
    public static final String NAME = "TitleTweaker";

    public TitleTweakerModule() {
        super(NAME, "Move titles, scale in-game titles, or route screen center titles directly to chat.");
        addOption(new SliderOption("Y Offset", 0, -400, 400, 5));
        addOption(new SliderOption("Scale", 100, 50, 200, 5));
        addOption(new BoolOption("Route to Chat", false));
        addOption(new BoolOption("Hide Screen Titles", false));
        setEnabled(false);
    }

    public static int getYOffset() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 0;
        Option o = m.getOption("Y Offset");
        return (o instanceof SliderOption so) ? so.getIntValue() : 0;
    }

    public static float getScale() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return 1.0f;
        Option o = m.getOption("Scale");
        return (o instanceof SliderOption so) ? so.getIntValue() / 100.0f : 1.0f;
    }

    public static boolean isHideScreenTitles() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Hide Screen Titles");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isRouteToChat() {
        Module m = ModuleManager.getInstance().getModule(NAME);
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Route to Chat");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    /**
     * Intercepts incoming title/subtitle and routes to chat if enabled.
     * Returns true if the screen title should be suppressed.
     */
    public static boolean handleTitle(String title, String subtitle) {
        if (!isRouteToChat() && !isHideScreenTitles()) return false;
        if (isRouteToChat()) {
            LadsGameBridge bridge = LadsGameBridge.get();
            if (bridge != null) {
                if (title != null && !title.isEmpty()) bridge.displayChatMessage("[Title] " + title);
                if (subtitle != null && !subtitle.isEmpty()) bridge.displayChatMessage("[Subtitle] " + subtitle);
            }
        }
        return isHideScreenTitles();
    }
}
