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
        register(new AsyncModule(), Module.Category.MECHANIC);

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
        // 1.7.0: four hotbar slots by default; the 1.6.0 list stays available. New, so every config starts on the default.
        armor.addOption(new DropdownOption("Style", 0, "Hotbar Slots", "List"));
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

        // 1.7.0: Simple Voice Chat's HUD as Lads elements; on, they replace its fixed-position icons (26.x only: no SVC on 1.8.9).
        hud("Voice Chat", "Simple Voice Chat's talking, muted, deafened and disconnected icon.").addOption(new BoolOption("Show label", true));
        getModule("Voice Chat").setEnabled(true);
        hud("Voice Chat Group", "The players in your Simple Voice Chat group; talking players turn green.").setEnabled(true);

        HudModule pot = hud("Potion Effects", "Show your active potion effects.");
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));

        // Gameplay toggle modules
        FullbrightModule fb = new FullbrightModule();
        register(fb, Module.Category.MECHANIC);

        register(new ToggleSprintModule(), Module.Category.MECHANIC);

        register(new ZoomModule(), Module.Category.MECHANIC);
        register(new CustomFovModule(), Module.Category.MECHANIC);

        register(new SmoothHotbarModule(), Module.Category.MECHANIC);
        register(new DynamicLightsModule(), Module.Category.MECHANIC);

        // DamageTilt: the hurt tilt leans by the hit's direction (Directional) or the old fixed way, scaled by Intensity (26.x: on
        // top of Minecraft's own Damage Tilt setting). Off, each version keeps its vanilla tilt.
        Module odt = new Module(com.thelads.core.client.DamageTilt.MODULE, "Hurt camera tilt: towards the hit (Directional) or the old "
            + "fixed way. Intensity 0 turns it off.");
        odt.addOption(new BoolOption(com.thelads.core.client.DamageTilt.DIRECTIONAL, true));
        odt.addOption(new SliderOption(com.thelads.core.client.DamageTilt.INTENSITY, 100, 0, 100, 5));
        register(odt, Module.Category.MECHANIC);

        Module swing = new Module("LegacySwing", "Xbox 360 mining and hand swing matching the supplied Legacy Console showcase.");
        register(swing, Module.Category.MECHANIC);

        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new DropdownOption("Intensity", 1, "Low", "Normal", "High"));
        register(vb, Module.Category.MECHANIC);
        register(new OldAnimationsModule(), Module.Category.MECHANIC);
        register(new ItemPhysicsModule(), Module.Category.MECHANIC);

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
        // 1.7.0, both on by default: no 100-line history cap (ChatHistory), and chat text with its shadow.
        chatMod.addOption(new BoolOption("Infinite History", true));
        chatMod.addOption(new BoolOption("Text Shadow", true));
        chatMod.setEnabled(true);
        register(chatMod, Module.Category.HUD);
        Module chatHeads = new Module(com.thelads.core.client.ChatHeads.NAME, "The sender's head in chat, just before their name or at the start of the line: "
            + "from signed chat, or from player names in the tab list. Keep text aligned applies to Start of line.");
        chatHeads.addOption(new DropdownOption(com.thelads.core.client.ChatHeads.POSITION, 0, com.thelads.core.client.ChatHeads.POSITIONS));
        chatHeads.addOption(new BoolOption(com.thelads.core.client.ChatHeads.BY_NAME, true));
        chatHeads.addOption(new BoolOption(com.thelads.core.client.ChatHeads.ALIGNED, false));
        chatHeads.setEnabled(true);
        register(chatHeads, Module.Category.HUD);

        register(new AutoReconnectModule(), Module.Category.SERVER);

        Module packets = new Module("IgnorePacketErrors", "Stay connected when the server sends a packet the client can't read or handle: it is skipped and logged "
            + "instead of disconnecting with Network Protocol Error. Warning: a skipped packet can leave the world out of sync (missing blocks, "
            + "entities or inventory changes) until you rejoin, and a broken or hostile server can keep you connected; turn it off on servers you "
            + "don't trust. Timeouts, kicks and lost connections still disconnect.");
        packets.setEnabled(true);
        register(packets, Module.Category.SERVER);

        register(new DiscordRpcModule(), Module.Category.SERVER);

        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new SliderOption("Size", 100, 50, 150, 25));
        sb.addOption(new SliderOption("X Offset", 0, -40, 40, 20));
        sb.addOption(new SliderOption("Y Offset", 0, -40, 40, 20));
        sb.addOption(new DropdownOption("Background", 0, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", true));
        // 1.7.0: the scoreboard shows its own colours unless this is on (1.6.0 always tinted it with the HUD colour).
        sb.addOption(new BoolOption("Custom Text Color", false));
        sb.addOption(new BoolOption("Hide Red Numbers", false));
        sb.addOption(new BoolOption("Hide Sequential Only", false));
        register(sb, Module.Category.HUD);

        Module tab = new TabListModule();
        register(tab, Module.Category.HUD);


        register(new BetterResolutionModule());

        register(new XaeroWorldMapModule(), Module.Category.SERVER);
        register(new JeiModule());

        Module sl = new Module("ScalableLux", "Highly optimized starlight lighting engine.");
        sl.setEnabled(true);
        register(sl);

        Module farBlock = new Module("FarBlockEntities", "Render block entities further away.");
        farBlock.addOption(new SliderOption("Distance", 128, 64, 256, 16));
        farBlock.setEnabled(true);
        register(farBlock);

        register(new RaisedModule());

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
        register(new FlashbackModule());

        Module decentScreenshot = new Module("BetterScreenshots", "Better screenshot saving and GUI.");
        decentScreenshot.setEnabled(true);
        register(decentScreenshot);

        register(new ClumpsModule(), Module.Category.MECHANIC);
        register(new SkinLayersModule(), Module.Category.MECHANIC);
        register(new Module("Lithium", "Optimize game logic, physics and world ticking."), Module.Category.MECHANIC);
        register(new Module("FerriteCore", "Reduce memory used by Minecraft's block and model data."), Module.Category.MECHANIC);
        register(new Module("XaeroMinimap", "Minimap, waypoints and navigation controls."), Module.Category.HUD);
        register(new ShulkerBoxUtilsModule(), Module.Category.MECHANIC);
        register(new MouseTweaksModule(), Module.Category.MECHANIC);
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
