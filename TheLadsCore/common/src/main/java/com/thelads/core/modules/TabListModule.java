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
        addOption(new DropdownOption("Background", 0, "Default", "Dark", "Light", "Off"));
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
    }
}
