// TabTweaks 1.5.11 configuration adapted from MicrocontrollersDev, LGPL-3.0-only.
package com.thelads.core.modules;

import com.thelads.core.config.*;
import com.thelads.core.config.Module;

/** Layout controls consumed by the same native engine as PingView. */
public final class TabListModule extends Module {
    public TabListModule() {
        super("TabList", "Resize, reposition and restyle the player tab list.");
        addOption(new SliderOption("Size", 100, 10, 200, 1));
        addOption(new SliderOption("X Offset", 0, -1000, 1000, 1));
        addOption(new SliderOption("Y Offset", 10, -1000, 1000, 1));
        addOption(new DropdownOption("Background", 3, "Default", "Dark", "Light", "Off"));
        addOption(new BoolOption("Text Shadow", true));
        addOption(new SliderOption("Max Players", 80, 1, 300, 1));
        addOption(new SliderOption("Players Per Column", 20, 1, 100, 1));
        addOption(new BoolOption("Hide Header", false));
        addOption(new BoolOption("Hide Footer", false));
        addOption(new BoolOption("Player Skins", true));
        addOption(new BoolOption("Show Player Count", false));
        addOption(new StringOption("Player Count Format", "Players: {count}"));
        addOption(new BoolOption("Hide Heads", false));
        addOption(new BoolOption("Hide NPC Heads", false));
        addOption(new BoolOption("Improved Hats", true));
        addOption(new BoolOption("Hide Objectives", false));
        addOption(new BoolOption("Header Shadow", true));
        addOption(new BoolOption("Body Shadow", true));
        addOption(new BoolOption("Footer Shadow", true));
        addOption(new BoolOption("Below Boss Bars", true));
        addOption(new ColorOption("Header Color", false, 0x80000000));
        addOption(new ColorOption("Body Color", false, 0x80000000));
        addOption(new ColorOption("Global Tab Background", false, 0x80000000));
        addOption(new ColorOption("Footer Color", false, 0x80000000));
        addOption(new ColorOption("Player Row Color", false, 0x20ffffff));
        selfFirstOption = addOption(new BoolOption("Self First", false));
        animatedOption = addOption(new BoolOption("Animated Open/Close", false));
        keyModeOption = addOption(new DropdownOption("Key Mode", 0, "Hold", "Toggle"));
        setEnabled(true);
    }

    // OneClient VanillaHUD's tab list extras, read by each version's tab list hooks.
    private final BoolOption selfFirstOption, animatedOption;
    private final DropdownOption keyModeOption;
    /** Cached each tick: your own row first, the list opens and closes animated, the key toggles it. */
    public boolean selfFirst, animated, toggle;
    private boolean keyWasDown, toggled;
    private float openness;
    private long lastFrame;

    public void refresh() {
        boolean on = isEnabled();
        selfFirst = on && selfFirstOption.get();
        animated = on && animatedOption.get();
        toggle = on && keyModeOption.getIndex() == 1;
        if (!toggle) toggled = false;
    }

    /** Whether the list is wanted open this frame: while the key is held, or (Key Mode Toggle) from one press of the key to the next. */
    public boolean wanted(boolean keyDown) {
        if (keyDown && !keyWasDown) toggled = !toggled;
        keyWasDown = keyDown;
        return toggle ? toggled : keyDown;
    }

    /** How open the list was drawn last (0 to 1): the scale its render uses. */
    public float openness() {
        return openness;
    }

    /**
     * The tab key's state as everything sees it (each version's KeyMapping.isDown hook, only with Toggle or Animated Open/Close on):
     * wanted open, or still closing.
     */
    public boolean tabKey(boolean physicallyDown, long nanos) {
        return openness(wanted(physicallyDown), nanos) > 0;
    }

    /** How open the list is drawn this frame (0 to 1, 150 ms each way with Animated Open/Close); it is drawn while above 0. */
    public float openness(boolean wanted, long nanos) {
        if (!animated) {
            lastFrame = 0;
            return openness = wanted ? 1 : 0;
        }
        float step = lastFrame == 0 ? 0 : Math.min(nanos - lastFrame, 50_000_000L) / 150e6f;
        lastFrame = nanos;
        return openness = wanted ? Math.min(1, openness + step) : Math.max(0, openness - step);
    }

    /** Formats player count into the format string (e.g. "Players: {count}"); defaults to "Players: {count}". */
    public static String formatPlayerCount(String format, int count) {
        String template = (format == null || format.isEmpty()) ? "Players: {count}" : format;
        return template.replace("{count}", String.valueOf(count));
    }

    /**
     * Composes the rendered footer text given the server footer and the TabList module options.
     * When there is no server footer (or it is empty/hidden), the player count is used directly without leading newlines or nulls.
     */
    public static String composeFooterText(String serverFooter, boolean removeFooter, boolean showPlayerCount, String format, int count) {
        String base = removeFooter ? null : serverFooter;
        if (showPlayerCount) {
            String countLine = formatPlayerCount(format, count);
            if (base == null || base.trim().isEmpty()) return countLine;
            return base + "\n" + countLine;
        }
        return base;
    }
}
