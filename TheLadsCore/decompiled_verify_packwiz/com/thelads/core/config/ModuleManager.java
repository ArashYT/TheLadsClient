/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.config;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.modules.DiscordRpcModule;
import com.thelads.core.modules.DynamicFPSModule;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.HudModule;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.modules.PerformanceManagerModule;
import com.thelads.core.modules.PingViewModule;
import com.thelads.core.modules.SmoothHotbarModule;
import com.thelads.core.modules.TexturePacksModule;
import com.thelads.core.modules.ToggleNametagsModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new LinkedHashMap<String, Module>();

    public ModuleManager() {
        this.register(new PingViewModule());
        this.register(new BetterF3Module());
        this.register(new PerformanceManagerModule());
        HudModule fps = this.hud("FPS", "Show your current FPS on screen.");
        fps.addOption(new CycleOption("Update rate", 1, "Instant", "Fast", "Normal", "Slow"));
        fps.addOption(new BoolOption("Smooth", true));
        HudModule coords = this.hud("Coordinates", "Show your XYZ coordinates.");
        coords.addOption(new CycleOption("Format", 0, "X Y Z", "Coords: X, Y, Z", "Labeled X/Y/Z"));
        coords.addOption(new BoolOption("Vertical", false));
        coords.addOption(new BoolOption("Per-axis colors", false));
        coords.addOption(new ColorOption("X Color", false, -43691));
        coords.addOption(new ColorOption("Y Color", false, -11141291));
        coords.addOption(new ColorOption("Z Color", false, -11167233));
        HudModule biome = this.hud("Biome", "Show the biome you're standing in.");
        biome.addOption(new CycleOption("Format", 0, "Name", "ID"));
        biome.addOption(new BoolOption("Show label", false));
        HudModule ping = this.hud("PingHUD", "Show your latency on screen.");
        ping.addOption(new BoolOption("Show label", true));
        ping.addOption(new BoolOption("Color by ping", true));
        HudModule armor = this.hud("ArmorHUD", "Show armor pieces and durability.");
        armor.addOption(new CycleOption("Durability", 1, "Off", "Number", "Percent"));
        armor.addOption(new BoolOption("Attach to hotbar", true));
        HudModule mem = this.hud("Memory", "Show JVM memory usage.");
        mem.addOption(new CycleOption("Display", 1, "Used / Max", "Used / Max + %", "Percent only"));
        HudModule dir = this.hud("Direction", "Show the direction you're facing.");
        dir.addOption(new CycleOption("Format", 0, "Cardinal", "Degrees", "Both"));
        dir.addOption(new BoolOption("Long names", false));
        HudModule speed = this.hud("Speed", "Show horizontal speed (blocks/sec).");
        speed.addOption(new CycleOption("Unit", 0, "b/s", "km/h"));
        speed.addOption(new CycleOption("Precision", 1, "0 dec", "1 dec", "2 dec"));
        HudModule day = this.hud("Day", "Show the in-world day count.");
        day.addOption(new BoolOption("Show label", true));
        HudModule time = this.hud("Time", "Show the in-world time of day.");
        time.addOption(new BoolOption("12-hour", false));
        time.addOption(new BoolOption("Show label", false));
        HudModule health = this.hud("Health", "Show your health points.");
        health.addOption(new CycleOption("Format", 0, "x/max", "HP x/max", "x", "Percent"));
        health.addOption(new BoolOption("Show absorption", true));
        HudModule hunger = this.hud("Hunger", "Show your food level.");
        hunger.addOption(new BoolOption("Show label", true));
        hunger.addOption(new BoolOption("Show saturation", false));
        HudModule xp = this.hud("XP", "Show your experience level.");
        xp.addOption(new CycleOption("Format", 0, "Level", "Progress %", "Both"));
        HudModule keys = this.hud("Keystrokes", "Animated WASD + mouse key display with CPS.");
        keys.addOption(new BoolOption("Show CPS", true));
        keys.addOption(new BoolOption("Show space bar", true));
        HudModule cps = this.hud("CPS", "Show left/right clicks per second.");
        cps.addOption(new CycleOption("Show", 0, "Both", "Left only", "Right only"));
        cps.addOption(new BoolOption("Show label", true));
        this.hud("XaeroMinimap", "Xaero's Minimap display.");
        this.hud("VoiceChat", "Simple Voice Chat HUD.");
        TexturePacksModule tp = new TexturePacksModule();
        tp.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%", "200%"));
        tp.addOption(new ColorOption("Background", true, Integer.MIN_VALUE));
        tp.addOption(new CycleOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        tp.addOption(new BoolOption("Show All", false));
        tp.addOption(new CycleOption("Max Packs", 2, "1", "2", "3", "4", "5", "6", "7", "8"));
        tp.addOption(new BoolOption("Show Hidden", false));
        this.register(tp);
        HudModule pot = this.hud("Potions", "Show your active potion effects.");
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));
        FullbrightModule fb = new FullbrightModule();
        fb.addOption(new CycleOption("Level", 2, "High", "Very High", "Max", "Infinite"));
        this.register(fb);
        ToggleSprintModule ts = new ToggleSprintModule();
        ts.addOption(new BoolOption("Disable on sneak", true));
        this.register(ts);
        ToggleSneakModule tsn = new ToggleSneakModule();
        tsn.addOption(new BoolOption("Disable on jump", false));
        this.register(tsn);
        ZoomModule zm = new ZoomModule();
        zm.addOption(new BoolOption("Hand Zoom", true));
        this.register(zm);
        this.register(new SmoothHotbarModule());
        Module odt = new Module("OldDamageTilt", "Old-style screen tilt when you take damage.");
        odt.addOption(new CycleOption("Intensity", 1, "Subtle", "Normal", "Strong"));
        this.register(odt);
        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new CycleOption("Intensity", 1, "Low", "Normal", "High"));
        this.register(vb);
        DynamicFPSModule dynFps = new DynamicFPSModule();
        dynFps.addOption(new CycleOption("Unfocused FPS", 2, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        dynFps.addOption(new CycleOption("AFK FPS", 3, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        this.register(dynFps);
        ToggleNametagsModule tnm = new ToggleNametagsModule();
        tnm.addOption(new CycleOption("Hide", 0, "All", "Players", "Mobs"));
        this.register(tnm);
        this.register(new KillBannerModule());
        Module chatInd = new Module("HideChatIndicators", "Hide chat signing/'modified' indicator bars.");
        chatInd.setEnabled(true);
        this.register(chatInd);
        Module ar = new Module("AutoReconnect", "Automatically rejoin the server after a disconnect.");
        ar.addOption(new CycleOption("Delay", 1, "3s", "5s", "10s", "30s"));
        ar.addOption(new CycleOption("Max attempts", 3, "1", "2", "3", "Unlimited"));
        this.register(ar);
        this.register(new DiscordRpcModule());
        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%"));
        sb.addOption(new CycleOption("X Offset", 2, "-40", "-20", "0", "+20", "+40"));
        sb.addOption(new CycleOption("Y Offset", 2, "-40", "-20", "0", "+20", "+40"));
        sb.addOption(new CycleOption("Background", 0, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", false));
        this.register(sb);
        Module tab = new Module("TabList", "Resize, reposition and restyle the player tab list.");
        tab.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%"));
        tab.addOption(new CycleOption("X Offset", 2, "-40", "-20", "0", "+20", "+40"));
        tab.addOption(new CycleOption("Y Offset", 2, "-40", "-20", "0", "+20", "+40"));
        tab.addOption(new CycleOption("Background", 0, "Default", "Dark", "Light", "Off"));
        tab.addOption(new BoolOption("Text Shadow", true));
        this.register(tab);
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private void register(Module module) {
        this.modules.put(module.getName(), module);
    }

    private HudModule hud(String name, String description) {
        HudModule m = new HudModule(name, description);
        m.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%", "200%"));
        m.addOption(new ColorOption("Background", true, Integer.MIN_VALUE));
        m.addOption(new CycleOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        this.register(m);
        return m;
    }

    public Collection<Module> getModules() {
        return this.modules.values();
    }

    public Collection<Module> getRegisteredModules() {
        return this.modules.values();
    }

    public Module getModule(String name) {
        return this.modules.get(name);
    }
}

