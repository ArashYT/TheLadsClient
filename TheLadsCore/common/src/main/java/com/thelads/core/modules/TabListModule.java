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
        addOption(new BoolOption("Self First", false));
        addOption(new BoolOption("Animated Open/Close", false));
        addOption(new DropdownOption("Key Mode", 0, "Hold", "Toggle"));
        setEnabled(true);
    }

    /** Formats player count into the format string (e.g. "Players: {count}"); defaults to "Players: {count}". */
    public static boolean isSelfFirst() {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Self First");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isAnimatedOpenClose() {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Animated Open/Close");
        return (o instanceof BoolOption bo) ? bo.get() : false;
    }

    public static boolean isToggleMode() {
        Module m = ModuleManager.getInstance().getModule("TabList");
        if (m == null || !m.isEnabled()) return false;
        Option o = m.getOption("Key Mode");
        return (o instanceof DropdownOption dop) && dop.getIndex() == 1;
    }

    public static java.util.List<String> sortSelfFirst(java.util.List<String> playerList, String selfName) {
        if (!isSelfFirst() || selfName == null || playerList == null || playerList.isEmpty()) return playerList;
        java.util.List<String> sorted = new java.util.ArrayList<>(playerList);
        for (int i = 0; i < sorted.size(); i++) {
            if (selfName.equalsIgnoreCase(sorted.get(i))) {
                if (i > 0) {
                    String self = sorted.remove(i);
                    sorted.add(0, self);
                }
                break;
            }
        }
        return sorted;
    }

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
