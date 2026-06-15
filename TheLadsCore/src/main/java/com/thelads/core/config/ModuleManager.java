package com.thelads.core.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.thelads.core.modules.DynamicFPSModule;
import com.thelads.core.modules.PingViewModule;
import com.thelads.core.modules.HudModule;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.modules.BetterF3Module;
import com.thelads.core.modules.PerformanceManagerModule;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        // Behaviour modules (DynamicFPS registered later with per-situation options)
        register(new PingViewModule());
        register(new BetterF3Module());
        register(new PerformanceManagerModule());

        // HUD overlay modules (rendered by HudManager, all disabled by default).
        // Every HUD module has a colour option (via the picker) + these extras.
        HudModule fps = hud("FPS", "Show your current FPS on screen.");
        fps.addOption(new CycleOption("Update rate", 1, "Instant", "Fast", "Normal", "Slow"));
        fps.addOption(new BoolOption("Smooth", true));

        HudModule coords = hud("Coordinates", "Show your XYZ coordinates.");
        coords.addOption(new CycleOption("Format", 0, "X Y Z", "Coords: X, Y, Z", "Labeled X/Y/Z"));
        coords.addOption(new BoolOption("Vertical", false));
        coords.addOption(new BoolOption("Per-axis colors", false));
        coords.addOption(new ColorOption("X Color", false, 0xFFFF5555));
        coords.addOption(new ColorOption("Y Color", false, 0xFF55FF55));
        coords.addOption(new ColorOption("Z Color", false, 0xFF5599FF));

        HudModule biome = hud("Biome", "Show the biome you're standing in.");
        biome.addOption(new CycleOption("Format", 0, "Name", "ID"));
        biome.addOption(new BoolOption("Show label", false));

        HudModule ping = hud("PingHUD", "Show your latency on screen.");
        ping.addOption(new BoolOption("Show label", true));
        ping.addOption(new BoolOption("Color by ping", true));

        HudModule armor = hud("ArmorHUD", "Show armor pieces and durability.");
        armor.addOption(new CycleOption("Durability", 1, "Off", "Number", "Percent"));
        armor.addOption(new BoolOption("Attach to hotbar", true));

        HudModule mem = hud("Memory", "Show JVM memory usage.");
        mem.addOption(new CycleOption("Display", 1, "Used / Max", "Used / Max + %", "Percent only"));

        HudModule dir = hud("Direction", "Show the direction you're facing.");
        dir.addOption(new CycleOption("Format", 0, "Cardinal", "Degrees", "Both"));
        dir.addOption(new BoolOption("Long names", false));

        HudModule speed = hud("Speed", "Show horizontal speed (blocks/sec).");
        speed.addOption(new CycleOption("Unit", 0, "b/s", "km/h"));
        speed.addOption(new CycleOption("Precision", 1, "0 dec", "1 dec", "2 dec"));

        HudModule day = hud("Day", "Show the in-world day count.");
        day.addOption(new BoolOption("Show label", true));

        HudModule time = hud("Time", "Show the in-world time of day.");
        time.addOption(new BoolOption("12-hour", false));
        time.addOption(new BoolOption("Show label", false));

        HudModule health = hud("Health", "Show your health points.");
        health.addOption(new CycleOption("Format", 0, "x/max", "HP x/max", "x", "Percent"));
        health.addOption(new BoolOption("Show absorption", true));

        HudModule hunger = hud("Hunger", "Show your food level.");
        hunger.addOption(new BoolOption("Show label", true));
        hunger.addOption(new BoolOption("Show saturation", false));

        HudModule xp = hud("XP", "Show your experience level.");
        xp.addOption(new CycleOption("Format", 0, "Level", "Progress %", "Both"));

        HudModule keys = hud("Keystrokes", "Animated WASD + mouse key display with CPS.");
        keys.addOption(new BoolOption("Show CPS", true));
        keys.addOption(new BoolOption("Show space bar", true));

        HudModule cps = hud("CPS", "Show left/right clicks per second.");
        cps.addOption(new CycleOption("Show", 0, "Both", "Left only", "Right only"));
        cps.addOption(new BoolOption("Show label", true));

        hud("XaeroMinimap", "Xaero's Minimap display.");
        hud("VoiceChat", "Simple Voice Chat HUD.");
        com.thelads.core.modules.TexturePacksModule tp = new com.thelads.core.modules.TexturePacksModule();
        tp.addOption(new com.thelads.core.config.CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%", "200%"));
        tp.addOption(new com.thelads.core.config.ColorOption("Background", true, 0x80000000));
        tp.addOption(new com.thelads.core.config.CycleOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        tp.addOption(new BoolOption("Show All", false));
        tp.addOption(new CycleOption("Max Packs", 2, "1", "2", "3", "4", "5", "6", "7", "8"));
        tp.addOption(new BoolOption("Show Hidden", false));
        register(tp);

        HudModule pot = hud("Potions", "Show your active potion effects.");
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));

        // Gameplay toggle modules (driven by client tick, no mixins)
        FullbrightModule fb = new FullbrightModule();
        fb.addOption(new CycleOption("Level", 2, "High", "Very High", "Max", "Infinite"));
        register(fb);

        ToggleSprintModule ts = new ToggleSprintModule();
        ts.addOption(new BoolOption("Disable on sneak", true));
        register(ts);

        ToggleSneakModule tsn = new ToggleSneakModule();
        tsn.addOption(new BoolOption("Disable on jump", false));
        register(tsn);
        ZoomModule zm = new ZoomModule();
        zm.addOption(new BoolOption("Hand Zoom", true));
        register(zm);

        register(new com.thelads.core.modules.SmoothHotbarModule());

        // Camera modules (applied by GameRendererMixin)
        Module odt = new Module("OldDamageTilt", "Old-style screen tilt when you take damage.");
        odt.addOption(new CycleOption("Intensity", 1, "Subtle", "Normal", "Strong"));
        register(odt);
        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new CycleOption("Intensity", 1, "Low", "Normal", "High"));
        register(vb);

        // Dynamic FPS per-situation
        com.thelads.core.modules.DynamicFPSModule dynFps = new com.thelads.core.modules.DynamicFPSModule();
        dynFps.addOption(new CycleOption("Unfocused FPS", 2, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        dynFps.addOption(new CycleOption("AFK FPS",       3, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        register(dynFps);

        // Gameplay visibility / chat (replacements for external mods)
        com.thelads.core.modules.ToggleNametagsModule tnm = new com.thelads.core.modules.ToggleNametagsModule();
        tnm.addOption(new CycleOption("Hide", 0, "All", "Players", "Mobs"));
        register(tnm);

        register(new com.thelads.core.modules.KillBannerModule());

        Module chatInd = new Module("HideChatIndicators", "Hide chat signing/'modified' indicator bars.");
        chatInd.setEnabled(true);
        register(chatInd);

        // Multiplayer
        Module ar = new Module("AutoReconnect", "Automatically rejoin the server after a disconnect.");
        ar.addOption(new CycleOption("Delay", 1, "3s", "5s", "10s", "30s"));
        ar.addOption(new CycleOption("Max attempts", 3, "1", "2", "3", "Unlimited"));
        register(ar);
        register(new com.thelads.core.modules.DiscordRpcModule());

        // Overlay modules (scoreboard via GuiMixin, tab list via PlayerTabOverlayMixin)
        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%"));
        sb.addOption(new CycleOption("X Offset", 2, "-40", "-20", "0", "+20", "+40"));
        sb.addOption(new CycleOption("Y Offset", 2, "-40", "-20", "0", "+20", "+40"));
        sb.addOption(new CycleOption("Background", 0, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", false));
        sb.addOption(new BoolOption("Hide Red Numbers", false));
        register(sb);

        Module tab = new Module("TabList", "Resize, reposition and restyle the player tab list.");
        tab.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%"));
        tab.addOption(new CycleOption("X Offset", 2, "-40", "-20", "0", "+20", "+40"));
        tab.addOption(new CycleOption("Y Offset", 2, "-40", "-20", "0", "+20", "+40"));
        tab.addOption(new CycleOption("Background", 0, "Default", "Dark", "Light", "Off"));
        tab.addOption(new BoolOption("Text Shadow", true));
        register(tab);

        // Capes Module
        Module capes = new Module("Capes", "Configure and toggle custom cape rendering providers.");
        capes.addOption(new CycleOption("Preferred Cape", 0, "Minecraft", "OptiFine", "LabyMod", "MinecraftCapes", "Cosmetica", "Cloaks+") {
            @Override
            public int getIndex() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null && config.getClientCapeType() != null) {
                    return config.getClientCapeType().ordinal();
                }
                return super.getIndex();
            }
            @Override
            public void setIndex(int i) {
                super.setIndex(i);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    int idx = getIndex();
                    if (idx >= 0 && idx < com.thelads.core.client.capes.CapeType.values().length) {
                        config.setClientCapeType(com.thelads.core.client.capes.CapeType.values()[idx]);
                        config.save();
                    }
                }
            }
        });
        capes.addOption(new BoolOption("OptiFine Capes", true) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableOptifine() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableOptifine(val);
                    config.save();
                }
            }
        });
        capes.addOption(new BoolOption("LabyMod Capes", false) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableLabyMod() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableLabyMod(val);
                    config.save();
                }
            }
        });
        capes.addOption(new BoolOption("MinecraftCapes", true) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableMinecraftCapesMod() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableMinecraftCapesMod(val);
                    config.save();
                }
            }
        });
        capes.addOption(new BoolOption("Cosmetica Capes", true) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableCosmetica() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableCosmetica(val);
                    config.save();
                }
            }
        });
        capes.addOption(new BoolOption("CloaksPlus Capes", true) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableCloaksPlus() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableCloaksPlus(val);
                    config.save();
                }
            }
        });
        capes.addOption(new BoolOption("Elytra Texture", true) {
            @Override
            public boolean get() {
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                return config != null ? config.getEnableElytraTexture() : super.get();
            }
            @Override
            public void set(boolean val) {
                super.set(val);
                var config = com.thelads.core.client.capes.Capes.getCONFIG();
                if (config != null) {
                    config.setEnableElytraTexture(val);
                    config.save();
                }
            }
        });
        capes.setEnabled(true);
        register(capes);

        // Render Scale Module
        Module rs = new Module("RenderScale", "Optimize performance by scaling 3D rendering resolution.");
        rs.addOption(new CycleOption("Preset", 0, "Custom", "Ultra Performance", "Balanced", "Quality", "Super Sampling"));
        rs.addOption(new CycleOption("Scale", 2, "50%", "75%", "100%", "125%", "150%", "200%"));
        rs.addOption(new CycleOption("Algorithm", 0, "Linear", "Nearest"));
        rs.addOption(new BoolOption("Dynamic Resolution", false));
        rs.addOption(new CycleOption("Target FPS", 1, "30", "60", "90", "120", "144", "Unlimited"));
        rs.addOption(new CycleOption("Min Scale", 0, "50%", "75%", "100%"));
        rs.setEnabled(true);
        register(rs);

        // Xaero's Fullscreen World Map
        Module wm = new Module("XaeroWorldmap", "Self-writing fullscreen map of the world.");
        wm.setEnabled(true);
        register(wm);

        // ScalableLux (Starlight) Lighting Engine
        Module sl = new Module("ScalableLux", "Highly optimized starlight lighting engine that fixes lighting performance and errors.");
        sl.setEnabled(true);
        register(sl);
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private void register(Module module) {
        modules.put(module.getName(), module);
    }

    /** Create + register a HUD module and return it (for adding options). */
    private HudModule hud(String name, String description) {
        HudModule m = new HudModule(name, description);
        // Every HUD module is resizable (25% steps), has a background colour, and a
        // colour mode (static or animated chroma/fade).
        m.addOption(new CycleOption("Size", 2, "50%", "75%", "100%", "125%", "150%", "200%"));
        m.addOption(new ColorOption("Background", true, 0x80000000));
        m.addOption(new CycleOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        register(m);
        return m;
    }

    public Collection<Module> getModules() {
        return modules.values();
    }

    public Collection<Module> getRegisteredModules() {
        return modules.values();
    }

    public Module getModule(String name) {
        return modules.get(name);
    }
}
