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
import com.thelads.core.modules.DynamicLightsModule;
import com.thelads.core.modules.AppleSkinModule;
import com.thelads.core.modules.EnhancedTooltipsModule;
import com.thelads.core.modules.EnhancedToolbarsModule;
import com.thelads.core.modules.NotEnoughAnimationsModule;
import com.thelads.core.modules.BetterStatsModule;
import com.thelads.core.modules.SkinLayersModule;
import com.thelads.core.modules.ImmediatelyFastModule;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        // Behaviour modules (DynamicFPS registered later with per-situation options)
        register(new PingViewModule(), Module.Category.SERVER);
        register(new BetterF3Module(), Module.Category.MECHANIC);
        register(new PerformanceManagerModule(), Module.Category.MECHANIC);

        // HUD overlay modules (rendered by HudManager, all disabled by default).
        // Every HUD module has a colour option (via the picker) + these extras.
        HudModule fps = hud("FPS", "Show your current FPS on screen.");
        fps.addOption(new DropdownOption("Update rate", 1, "Instant", "Fast", "Normal", "Slow"));
        fps.addOption(new BoolOption("Smooth", true));

        HudModule coords = hud("Coordinates", "Show your XYZ coordinates.");
        coords.addOption(new DropdownOption("Format", 0, "X Y Z", "Coords: X, Y, Z", "Labeled X/Y/Z"));
        coords.addOption(new BoolOption("Vertical", false));
        coords.addOption(new BoolOption("Per-axis colors", false));
        coords.addOption(new ColorOption("X Color", false, 0xFFFF5555));
        coords.addOption(new ColorOption("Y Color", false, 0xFF55FF55));
        coords.addOption(new ColorOption("Z Color", false, 0xFF5599FF));

        HudModule biome = hud("Biome", "Show the biome you're standing in.");
        biome.addOption(new DropdownOption("Format", 0, "Name", "ID"));
        biome.addOption(new BoolOption("Show label", false));

        HudModule ping = hud("PingHUD", "Show your latency on screen.");
        ping.addOption(new BoolOption("Show label", true));
        ping.addOption(new BoolOption("Color by ping", true));

        HudModule armor = hud("ArmorHUD", "Show armor pieces and durability.");
        armor.addOption(new DropdownOption("Durability", 1, "Off", "Number", "Percent"));
        armor.addOption(new BoolOption("Attach to hotbar", true));

        HudModule mem = hud("Memory", "Show JVM memory usage.");
        mem.addOption(new DropdownOption("Display", 1, "Used / Max", "Used / Max + %", "Percent only"));

        HudModule dir = hud("Direction", "Show the direction you're facing.");
        dir.addOption(new DropdownOption("Format", 0, "Cardinal", "Degrees", "Both"));
        dir.addOption(new BoolOption("Long names", false));

        HudModule speed = hud("Speed", "Show horizontal speed (blocks/sec).");
        speed.addOption(new DropdownOption("Unit", 0, "b/s", "km/h"));
        speed.addOption(new DropdownOption("Precision", 1, "0 dec", "1 dec", "2 dec"));

        HudModule day = hud("Day", "Show the in-world day count.");
        day.addOption(new BoolOption("Show label", true));

        HudModule time = hud("Time", "Show the in-world time of day.");
        time.addOption(new BoolOption("12-hour", false));
        time.addOption(new BoolOption("Show label", false));

        HudModule health = hud("Health", "Show your health points.");
        health.addOption(new DropdownOption("Format", 0, "x/max", "HP x/max", "x", "Percent"));
        health.addOption(new BoolOption("Show absorption", true));

        HudModule hunger = hud("Hunger", "Show your food level.");
        hunger.addOption(new BoolOption("Show label", true));
        hunger.addOption(new BoolOption("Show saturation", false));

        HudModule xp = hud("XP", "Show your experience level.");
        xp.addOption(new DropdownOption("Format", 0, "Level", "Progress %", "Both"));

        HudModule keys = hud("Keystrokes", "Animated WASD + mouse key display with CPS.");
        keys.addOption(new BoolOption("Show CPS", true));
        keys.addOption(new BoolOption("Show space bar", true));

        HudModule cps = hud("CPS", "Show left/right clicks per second.");
        cps.addOption(new DropdownOption("Show", 0, "Both", "Left only", "Right only"));
        cps.addOption(new BoolOption("Show label", true));

        hud("Scoreboard", "Vanilla Scoreboard.");

        register(new com.thelads.core.modules.TitleScaleModule());
        register(new com.thelads.core.modules.ExordiumModule(), Module.Category.MECHANIC);

        com.thelads.core.modules.TexturePacksModule tp = new com.thelads.core.modules.TexturePacksModule();
        tp.addOption(new com.thelads.core.config.SliderOption("Size", 100, 50, 200, 25));
        tp.addOption(new com.thelads.core.config.ColorOption("Background", true, 0x80000000));
        tp.addOption(new com.thelads.core.config.DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        tp.addOption(new BoolOption("Show All", false));
        tp.addOption(new DropdownOption("Max Packs", 2, "1", "2", "3", "4", "5", "6", "7", "8"));
        tp.addOption(new BoolOption("Show Hidden", false));
        tp.addOption(new BoolOption("Disable Hidden Overrides", false));
        register(tp);

        HudModule pot = hud("Potion Effects", "Show your active potion effects.");
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));

        // Gameplay toggle modules (driven by client tick, no mixins)
        FullbrightModule fb = new FullbrightModule();
        fb.addOption(new SliderOption("Brightness Multiplier", 1.0, 10.0, 1.0, 0.5));
        register(fb, Module.Category.MECHANIC);

        ToggleSprintModule ts = new ToggleSprintModule();
        ts.addOption(new DropdownOption("Mode", 0, "Toggle", "Always"));
        ts.addOption(new BoolOption("Disable on sneak", true));
        register(ts, Module.Category.MECHANIC);

        ToggleSneakModule tsn = new ToggleSneakModule();
        tsn.addOption(new DropdownOption("Mode", 0, "Toggle", "Always"));
        tsn.addOption(new BoolOption("Disable on jump", false));
        register(tsn, Module.Category.MECHANIC);
        ZoomModule zm = new ZoomModule();
        zm.addOption(new BoolOption("Hand Zoom", true));
        register(zm, Module.Category.MECHANIC);

        register(new com.thelads.core.modules.SmoothHotbarModule(), Module.Category.MECHANIC);
        register(new DynamicLightsModule(), Module.Category.MECHANIC);

        // Camera modules (applied by GameRendererMixin)
        Module odt = new Module("OldDamageTilt", "Old-style screen tilt when you take damage.");
        odt.addOption(new DropdownOption("Intensity", 1, "Subtle", "Normal", "Strong"));
        register(odt, Module.Category.MECHANIC);
        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new DropdownOption("Intensity", 1, "Low", "Normal", "High"));
        register(vb, Module.Category.MECHANIC);

        // Dynamic FPS per-situation
        com.thelads.core.modules.DynamicFPSModule dynFps = new com.thelads.core.modules.DynamicFPSModule();
        register(dynFps, Module.Category.MECHANIC);

        // Gameplay visibility / chat (replacements for external mods)
        com.thelads.core.modules.ToggleNametagsModule tnm = new com.thelads.core.modules.ToggleNametagsModule();
        register(tnm, Module.Category.MECHANIC);

        com.thelads.core.modules.TitleScreenModule tsm = new com.thelads.core.modules.TitleScreenModule();
        modules.put(tsm.getName(), tsm);

        com.thelads.core.modules.PaperdollModule pdm = new com.thelads.core.modules.PaperdollModule();
        modules.put(pdm.getName(), pdm);

        register(new com.thelads.core.modules.KillBannerModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.CrosshairModule(), Module.Category.MECHANIC);

        Module chatInd = new Module("HideChatIndicators", "Hide chat signing/'modified' indicator bars.");
        chatInd.setEnabled(true);
        register(chatInd);

        // Multiplayer
        Module ar = new Module("AutoReconnect", "Automatically rejoin the server after a disconnect.");
        ar.addOption(new DropdownOption("Delay", 1, "3s", "5s", "10s", "30s"));
        ar.addOption(new DropdownOption("Max attempts", 3, "1", "2", "3", "Unlimited"));
        register(ar, Module.Category.SERVER);
        register(new com.thelads.core.modules.DiscordRpcModule(), Module.Category.SERVER);

        // Overlay modules (scoreboard via GuiMixin, tab list via PlayerTabOverlayMixin)
        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new SliderOption("Size", 100, 50, 150, 25));
        sb.addOption(new SliderOption("X Offset", 0, -40, 40, 20));
        sb.addOption(new SliderOption("Y Offset", 0, -40, 40, 20));
        sb.addOption(new DropdownOption("Background", 0, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", false));
        sb.addOption(new BoolOption("Hide Red Numbers", false));
        register(sb);

        Module tab = new Module("TabList", "Resize, reposition and restyle the player tab list.");
        tab.addOption(new SliderOption("Size", 100, 50, 150, 25));
        tab.addOption(new SliderOption("X Offset", 0, -40, 40, 20));
        tab.addOption(new SliderOption("Y Offset", 0, -40, 40, 20));
        tab.addOption(new DropdownOption("Background", 0, "Default", "Dark", "Light", "Off"));
        tab.addOption(new BoolOption("Text Shadow", true));
        register(tab, Module.Category.HUD);

        // Capes Module
        Module capes = new Module("Capes", "Configure and toggle custom cape rendering providers.");
        capes.addOption(new DropdownOption("Preferred Cape", 0, "Minecraft", "OptiFine", "LabyMod", "MinecraftCapes", "Cosmetica", "Cloaks+") {
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
                    if (i >= 0 && i < com.thelads.core.client.capes.CapeType.values().length) {
                        config.setClientCapeType(com.thelads.core.client.capes.CapeType.values()[i]);
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
        rs.addOption(new DropdownOption("Preset", 0, "Custom", "Ultra Performance", "Balanced", "Quality", "Super Sampling"));
        rs.addOption(new SliderOption("Scale", 100, 50, 200, 25));
        rs.addOption(new DropdownOption("Algorithm", 0, "Linear", "Nearest"));
        rs.addOption(new BoolOption("Dynamic Resolution", false));
        rs.addOption(new DropdownOption("Target FPS", 1, "30", "60", "90", "120", "144", "Unlimited"));
        rs.addOption(new SliderOption("Min Scale", 100, 50, 100, 25));
        rs.setEnabled(true);
        register(rs);

        // Xaero's Fullscreen World Map
        com.thelads.core.modules.XaeroWorldMapModule wm = new com.thelads.core.modules.XaeroWorldMapModule();
        wm.setEnabled(true);
        register(wm, Module.Category.SERVER);

        // JeiModule
        com.thelads.core.modules.JeiModule jei = new com.thelads.core.modules.JeiModule();
        jei.setEnabled(true);
        register(jei);

        // ScalableLux (Starlight) Lighting Engine
        Module sl = new Module("ScalableLux", "Highly optimized starlight lighting engine that fixes lighting performance and errors.");
        sl.setEnabled(true);
        register(sl);

        // Ported features
        Module farBlock = new Module("FarBlockEntities", "Render block entities further away.");
        farBlock.setEnabled(true);
        register(farBlock);

        Module raised = new Module("Raised", "Moves the hotbar up when the chat is open.");
        raised.addOption(new SliderOption("Distance", 0, 50, 14, 1));
        raised.setEnabled(true);
        register(raised);

        AppleSkinModule appleSkin = new AppleSkinModule();
        appleSkin.setEnabled(true);
        register(appleSkin);

        EnhancedTooltipsModule enhancedTooltips = new EnhancedTooltipsModule();
        enhancedTooltips.setEnabled(true);
        register(enhancedTooltips);

        EnhancedToolbarsModule enhancedToolbars = new EnhancedToolbarsModule();
        enhancedToolbars.setEnabled(true);
        register(enhancedToolbars);

        NotEnoughAnimationsModule notEnoughAnimations = new NotEnoughAnimationsModule();
        notEnoughAnimations.setEnabled(true);
        register(notEnoughAnimations);

        BetterStatsModule betterStats = new BetterStatsModule();
        betterStats.setEnabled(true);
        register(betterStats);


        Module disableNarrator = new Module("DisableNarrator", "Disables the annoying narrator.");
        disableNarrator.setEnabled(true);
        register(disableNarrator);

        Module signalLoss = new Module("SignalLoss", "Show a warning when server connection is lost.");
        signalLoss.setEnabled(true);
        register(signalLoss);

        Module decentScreenshot = new Module("BetterScreenshots", "Better screenshot saving and GUI.");
        decentScreenshot.setEnabled(true);
        register(decentScreenshot);

        // Clumps
        com.thelads.core.modules.ClumpsModule clumps = new com.thelads.core.modules.ClumpsModule();
        clumps.setEnabled(true);
        register(clumps, Module.Category.MECHANIC);

        // SkinLayers & ImmediatelyFast
        register(new SkinLayersModule(), Module.Category.MECHANIC);
        register(new ImmediatelyFastModule(), Module.Category.MECHANIC);
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private void register(Module module) {
        modules.put(module.getName(), module);
    }

    private void register(Module module, Module.Category category) {
        module.setCategory(category);
        modules.put(module.getName(), module);
    }

    /** Create + register a HUD module and return it (for adding options). */
    private HudModule hud(String name, String description) {
        HudModule m = new HudModule(name, description);
        // Every HUD module is resizable (25% steps), has a background colour, and a
        // colour mode (static or animated chroma/fade).
        m.addOption(new SliderOption("Size", 100, 50, 200, 25));
        m.addOption(new ColorOption("Background", true, 0x80000000));
        m.addOption(new DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        m.setCategory(Module.Category.HUD);
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
