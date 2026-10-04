package com.thelads.core.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.thelads.core.modules.*;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager() {
        Module autohide=new Module("Autohide", "Fade the hotbar, status bars and Lads HUD widgets while idle; restore them on gameplay changes.");
        autohide.addOption(new SliderOption("Hide after seconds", 4, 1, 30, 1));
        autohide.addOption(new SliderOption("Fade milliseconds", 350, 0, 1500, 50));
        autohide.addOption(new BoolOption("Show while moving", false));
        autohide.addOption(new BoolOption("Show when hurt or hungry", true));
        autohide.setEnabled(true);register(autohide,Module.Category.HUD);
        Module jade=new Module("Jade", "Identify blocks and entities using the source-integrated Jade API and addon providers.");
        jade.addOption(new ActionOption("Settings and addons", "Open Jade settings"));jade.setEnabled(true);register(jade,Module.Category.HUD);

        hud("Clock", "Your local clock, with 12 or 24 hour display.").addOption(new BoolOption("12-hour clock", false));
        var stopwatch = hud("Stopwatch", "A local timer. Bind start/pause and reset in Controls; it continues across menus and worlds.");
        stopwatch.addOption(new ActionOption("Start or pause", "Start / pause timer"));
        stopwatch.addOption(new ActionOption("Reset", "Reset timer"));
        hud("ItemCounter", "Count held items, arrows, totems or rockets in your inventory and offhand.")
            .addOption(new DropdownOption("Item", 0, "Held item", "Arrows", "Totems", "Rockets"));
        hud("ReachDisplay", "Show the distance to your last client-observed attack point. Does not change reach.");
        hud("ServerAddress", "Show the connected server address, or hide it while streaming.")
            .addOption(new BoolOption("Hide address", false));
        hud("PortalCoordinates", "Overworld / Nether destination X and Z. Negative coordinates round down.");
        var tools = new Module("ClientTools", "Local coordinate copying, durability warnings and inventory alerts.");
        tools.addOption(new ActionOption("Copy coordinates", "Copy coordinates"));
        tools.addOption(new BoolOption("Low durability warning", true));
        tools.addOption(new SliderOption("Durability percent", 10, 1, 50, 1));
        tools.addOption(new BoolOption("Inventory full warning", true));
        register(tools, Module.Category.MECHANIC);
        var particles = new Module("ParticleBudget", "Limit only decorative smoke, leaves and spores. Gameplay indicators remain unchanged.");
        particles.addOption(new SliderOption("Particles per tick", 64, 8, 512, 8));
        particles.addOption(new SliderOption("Distance", 48, 16, 128, 8));
        register(particles, Module.Category.MECHANIC);

        // Behaviour modules
        register(new PingViewModule(), Module.Category.SERVER);
        register(new BetterF3Module(), Module.Category.MECHANIC);
        register(new PerformanceManagerModule(), Module.Category.MECHANIC);
        register(new ThreadPriorityModule(), Module.Category.MECHANIC);
        Module jasione = new Module("Jasione", "Cut garbage from enum lookups: Enum#values() calls that only read the array share one copy. Applies after a restart.");
        jasione.setEnabled(true);
        register(jasione, Module.Category.MECHANIC);

        // HUD overlay modules (rendered by HudManager)
        HudModule fps = hud("FPS", "Show your current FPS on screen.");
        fps.addOption(new DropdownOption("Update rate", 1, "Instant", "Fast", "Normal", "Slow"));
        fps.addOption(new BoolOption("Smooth", true));
        fps.addOption(new DropdownOption("Display", 0, "Game FPS", "HUD FPS", "Both"));

        Module minimap = new Module("Minimap", "Lads controls for the bundled Xaero engine. Uses your existing maps, waypoints and server rules.");
        minimap.setEnabled(true);
        minimap.addOption(new ActionOption("Map and waypoint settings", "Open Xaero settings"));
        register(minimap, Module.Category.HUD);

        HudModule boss = hud("BossBar", "Move, resize or hide boss bars and control their world effects.");
        boss.addOption(new BoolOption("Show bars", true));
        boss.addOption(new BoolOption("Show names", true));
        boss.addOption(new SliderOption("Maximum bars", 5, 1, 10, 1));
        boss.addOption(new BoolOption("Darken sky", true));
        boss.addOption(new BoolOption("Boss fog", true));
        boss.addOption(new BoolOption("Boss music", true));

        HudModule coords = hud("Coordinates", "Show your XYZ coordinates.");
        coords.addOption(new DropdownOption("Format", 2, "X Y Z", "Coords: X, Y, Z", "Labeled X/Y/Z"));
        coords.addOption(new BoolOption("Vertical", true));
        coords.addOption(new BoolOption("Show Biome", false));
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

        register(new TitleScaleModule());
        register(new ExordiumModule(), Module.Category.MECHANIC);

        TexturePacksModule tp = new TexturePacksModule();
        tp.addOption(new SliderOption("Size", 100, 50, 200, 25));
        tp.addOption(new ColorOption("Background", true, 0x80000000));
        tp.addOption(new DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        tp.addOption(new BoolOption("Show All", false));
        tp.addOption(new DropdownOption("Max Packs", 2, "1", "2", "3", "4", "5", "6", "7", "8"));
        register(tp, Module.Category.HUD);

        HudModule pot = hud("Potion Effects", "Show your active potion effects.");
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));

        // Gameplay toggle modules
        FullbrightModule fb = new FullbrightModule();
        fb.addOption(new SliderOption("Brightness Multiplier", 1.0, 1.0, 10.0, 0.5));
        register(fb, Module.Category.MECHANIC);

        ToggleSprintModule ts = new ToggleSprintModule();
        register(ts, Module.Category.MECHANIC);

        ToggleSneakModule tsn = new ToggleSneakModule();
        register(tsn, Module.Category.MECHANIC);

        register(new ZoomModule(), Module.Category.MECHANIC);

        register(new SmoothHotbarModule(), Module.Category.MECHANIC);
        register(new DynamicLightsModule(), Module.Category.MECHANIC);

        Module odt = new Module("OldDamageTilt", "Old-style screen tilt when you take damage.");
        odt.addOption(new DropdownOption("Intensity", 1, "Subtle", "Normal", "Strong"));
        register(odt, Module.Category.MECHANIC);

        Module swing = new Module("LegacySwing", "Xbox 360 mining and hand swing matching the supplied Legacy Console showcase.");
        register(swing, Module.Category.MECHANIC);

        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new DropdownOption("Intensity", 1, "Low", "Normal", "High"));
        register(vb, Module.Category.MECHANIC);
        register(new OldAnimationsModule(), Module.Category.MECHANIC);

        register(new DynamicFPSModule(), Module.Category.MECHANIC);
        register(new ToggleNametagsModule(), Module.Category.MECHANIC);
        register(new TitleScreenModule());
        register(new PaperdollModule());
        register(new KillBannerModule(), Module.Category.MECHANIC);
        register(new CrosshairModule(), Module.Category.MECHANIC);

        // Every chat feature lives here; ConfigManager migrates the old ClientTools timestamps and HideChatIndicators.
        Module chatMod = new Module("Chat", "Chat size, background, message animations, timestamps, signing indicators and screenshot links.");
        chatMod.addOption(new BoolOption("Chat Background", true));
        chatMod.addOption(new SliderOption("Chat Width", 320, 100, 600, 10));
        chatMod.addOption(new SliderOption("Chat Height", 180, 50, 400, 10));
        chatMod.addOption(new BoolOption("Message Animations", true));
        chatMod.addOption(new BoolOption("Timestamps", false));
        chatMod.addOption(new BoolOption("Hide Signing Indicators", true));
        chatMod.addOption(new BoolOption("Screenshot Link Buttons", true));
        chatMod.setEnabled(true);
        register(chatMod, Module.Category.HUD);

        register(new AutoReconnectModule(), Module.Category.SERVER);

        register(new DiscordRpcModule(), Module.Category.SERVER);

        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new SliderOption("Size", 100, 50, 150, 25));
        sb.addOption(new SliderOption("X Offset", 0, -40, 40, 20));
        sb.addOption(new SliderOption("Y Offset", 0, -40, 40, 20));
        sb.addOption(new DropdownOption("Background", 0, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", true));
        sb.addOption(new BoolOption("Hide Red Numbers", false));
        sb.addOption(new BoolOption("Hide Sequential Only", false));
        register(sb, Module.Category.HUD);

        Module tab = new TabListModule();
        register(tab, Module.Category.HUD);


        Module rs = new Module("RenderScale", "Scale world rendering while the HUD and menus stay at native resolution.");
        rs.addOption(new DropdownOption("Preset", 0, "Custom", "Ultra Performance", "Balanced", "Quality", "Super Sampling"));
        rs.addOption(new SliderOption("Scale", 100, 50, 200, 25));
        rs.addOption(new DropdownOption("Algorithm", 0, "Linear", "Nearest"));
        rs.addOption(new BoolOption("Dynamic Resolution", false));
        rs.addOption(new DropdownOption("Target FPS", 1, "30", "60", "90", "120", "144", "Unlimited"));
        rs.addOption(new SliderOption("Min Scale", 50, 50, 100, 25));
        rs.setEnabled(true);
        register(rs);

        register(new XaeroWorldMapModule(), Module.Category.SERVER);
        register(new JeiModule());

        Module sl = new Module("ScalableLux", "Highly optimized starlight lighting engine.");
        sl.setEnabled(true);
        register(sl);

        Module farBlock = new Module("FarBlockEntities", "Render block entities further away.");
        farBlock.addOption(new SliderOption("Distance", 128, 64, 256, 16));
        farBlock.setEnabled(true);
        register(farBlock);

        Module raised = new Module("Raised", "Moves the hotbar up when the chat is open.");
        raised.addOption(new SliderOption("Distance", 14, 0, 50, 1));
        raised.setEnabled(true);
        register(raised);

        register(new AppleSkinModule());
        register(new EnhancedTooltipsModule());
        register(new EnhancedToolbarsModule());
        register(new NotEnoughAnimationsModule());
        register(new BetterStatsModule());
        register(new Module("ModernAdvancements", "Search advancements, track goals and customize advancement toasts."));
        register(new Module("Resourcify", "Browse and update resource packs, data packs, shaders and worlds."));

        Module disableNarrator = new Module("DisableNarrator", "Disables narrator.");
        disableNarrator.setEnabled(true);
        register(disableNarrator);

        register(new SignalLossModule());

        Module decentScreenshot = new Module("BetterScreenshots", "Better screenshot saving and GUI.");
        decentScreenshot.setEnabled(true);
        register(decentScreenshot);

        register(new ClumpsModule(), Module.Category.MECHANIC);
        register(new SkinLayersModule(), Module.Category.MECHANIC);
        register(new Module("Lithium", "Optimize game logic, physics and world ticking."), Module.Category.MECHANIC);
        register(new Module("FerriteCore", "Reduce memory used by Minecraft's block and model data."), Module.Category.MECHANIC);
        register(new Module("XaeroMinimap", "Minimap, waypoints and navigation controls."), Module.Category.HUD);
        register(new ShulkerBoxUtilsModule(), Module.Category.MECHANIC);
        Module rawInput = new Module("RawInput", "Raw mouse input directly from hardware bypassing Windows acceleration.");
        rawInput.setEnabled(true);
        register(rawInput, Module.Category.MECHANIC);
        Module borderless = new Module("BorderlessFullscreen", "Borderless windowed fullscreen mode.");
        borderless.setEnabled(true);
        register(borderless, Module.Category.MECHANIC);
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    public void register(Module module) {
        modules.put(module.getName(), module);
    }

    public void register(Module module, Module.Category category) {
        module.setCategory(category);
        modules.put(module.getName(), module);
    }

    private HudModule hud(String name, String description) {
        HudModule m = new HudModule(name, description);
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
