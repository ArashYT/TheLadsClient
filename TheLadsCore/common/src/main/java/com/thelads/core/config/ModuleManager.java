package com.thelads.core.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.thelads.core.modules.*;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final java.util.Set<String> hiddenModuleNames = new java.util.HashSet<>();

    public ModuleManager() {
        Module autohide=new Module("Autohide", "Fade the hotbar, status bars and Lads HUD widgets while idle; restore them on gameplay changes.");
        autohide.addOption(new SliderOption("Hide after seconds", 4, 1, 30, 1));
        autohide.addOption(new SliderOption("Fade milliseconds", 350, 0, 1500, 50));
        autohide.addOption(new BoolOption("Show while moving", false));
        autohide.addOption(new BoolOption("Show when hurt or hungry", true));
        // 1.8.5: how far it fades, whether swinging, placing and using items bring it back (off: they do not), and what fades.
        autohide.addOption(new SliderOption("Faded opacity", 0, 0, 80, 5).percent());
        autohide.addOption(new BoolOption("Show when swinging or placing", false));
        autohide.addOption(new BoolOption("Fade vanilla HUD", true));
        autohide.addOption(new BoolOption("Fade Lads HUD", true));
        autohide.setEnabled(false);register(autohide,Module.Category.HUD);
        Module jade=new Module("Jade", "Identify blocks and entities using the source-integrated Jade API and addon providers.");
        jade.addOption(new ActionOption("Settings and addons", "Open Jade settings"));jade.setEnabled(true);registerHidden(jade,Module.Category.HUD);

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
        tools.setEnabled(true);
        register(tools, Module.Category.MECHANIC);
        // Particle Core replaced ParticleBudget in 1.8.5; ConfigManager.migrateParticleBudget carries the old settings over.
        register(new ParticleCoreModule(), Module.Category.MECHANIC);
        var culling = new Module("EntityCulling", "Skip drawing entities and block entities hidden behind solid blocks. Name tags still show through walls.");
        culling.setEnabled(true);
        registerHidden(culling, Module.Category.MECHANIC);

        // Behaviour modules
        register(new PingViewModule(), Module.Category.SERVER);
        register(new BetterF3Module(), Module.Category.MECHANIC);
        register(new PerformanceManagerModule(), Module.Category.MECHANIC);
        var threads = new ThreadPriorityModule();
        threads.setEnabled(true);
        register(threads, Module.Category.MECHANIC);
        Module jasione = new Module("Jasione", "Cut garbage from enum lookups: Enum#values() calls that only read the array share one copy. Applies after a restart.");
        jasione.setEnabled(true);
        registerHidden(jasione, Module.Category.MECHANIC);
        registerHidden(new AsyncModule(), Module.Category.MECHANIC);
        registerHidden(new OptiFine189Module(), Module.Category.MECHANIC);

        // HUD overlay modules (rendered by HudManager)
        HudModule fps = hud("FPS", "Show your current FPS on screen.");
        fps.addOption(new DropdownOption("Update rate", 1, "Instant", "Fast", "Normal", "Slow"));
        fps.addOption(new BoolOption("Smooth", true));
        fps.addOption(new DropdownOption("Display", 0, "Game FPS", "HUD FPS", "Both"));
        fps.setEnabled(true);

        Module minimap = new Module("Minimap", "Lads controls for the bundled Xaero engine. Uses your existing maps, waypoints and server rules.");
        minimap.setEnabled(true);
        minimap.addOption(new ActionOption("Map and waypoint settings", "Open Xaero settings"));
        register(minimap, Module.Category.HUD);

        HudModule boss = hud("BossBar", "Move, resize or hide boss bars and control their world effects.");
        boss.addOption(new BoolOption("Show bars", true));
        boss.addOption(new BoolOption("Show names", true));
        boss.addOption(new SliderOption("Maximum bars", 5, 1, 10, 1));
        boss.addOption(new BoolOption("Colored boss bar", true));
        boss.addOption(new BoolOption("Darken sky", true));
        boss.addOption(new BoolOption("Boss fog", true));
        boss.addOption(new BoolOption("Boss music", true));
        boss.setEnabled(true);

        // 1.9.1: the action bar as a HUD (ActionBarHudElement), on by default (owner); off, vanilla draws it unchanged. Its defaults
        // are vanilla's look and place, with a text shadow; Custom Text Color uses Text Color and Color mode instead of the server's colours.
        HudModule actionBar = hud(com.thelads.core.client.hud.ActionBar.MODULE, "Move, resize and restyle the action bar (server messages above the hotbar, \"Now playing\"), or hide it.");
        actionBar.setEnabled(true);
        actionBar.addOption(new BoolOption("Text Shadow", true));
        actionBar.addOption(new BoolOption("Show Background", false));
        actionBar.addOption(new BoolOption("Custom Text Color", false));
        actionBar.addOption(new SliderOption("Duration", 3, 1, 10, .5).suffix("s"));
        actionBar.addOption(new BoolOption("Fade Out", true));
        actionBar.addOption(new BoolOption("Hide Action Bar", false));

        HudModule coords = hud("Coordinates", "Show your XYZ coordinates.");
        coords.addOption(new DropdownOption("Format", 2, "X Y Z", "Coords: X, Y, Z", "Labeled X/Y/Z"));
        coords.addOption(new BoolOption("Vertical", true));
        coords.addOption(new BoolOption("Show Biome", false));
        coords.addOption(new BoolOption("Show Direction", true)); // 1.9.1: where you face (N, NE... and the axes), on the right
        coords.addOption(new BoolOption("Show C", false));
        coords.addOption(new BoolOption("Per-axis colors", false));
        coords.addOption(new ColorOption("X Color", false, 0xFFFF5555));
        coords.addOption(new ColorOption("Y Color", false, 0xFF55FF55));
        coords.addOption(new ColorOption("Z Color", false, 0xFF5599FF));
        coords.setEnabled(true);

        HudModule biome = hud("Biome", "Show the biome you're standing in.");
        biome.addOption(new DropdownOption("Format", 0, "Name", "ID"));
        biome.addOption(new BoolOption("Show label", false));

        HudModule ping = hud("PingHUD", "Show your latency on screen.");
        ping.addOption(new BoolOption("Show label", true));
        ping.addOption(new BoolOption("Color by ping", true));
        ping.setEnabled(true);

        HudModule armor = hud("ArmorHUD", "Show armor pieces and durability.");
        // 1.7.0: four hotbar slots by default; the 1.6.0 list stays available. New, so every config starts on the default.
        armor.addOption(new DropdownOption("Style", 0, "Hotbar Slots", "List"));
        armor.addOption(new DropdownOption("Durability", 1, "Off", "Number", "Percent"));
        armor.addOption(new BoolOption("Attach to hotbar", true));
        armor.setEnabled(true);

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

        KeystrokesModule keys = new KeystrokesModule();
        keys.setCategory(Module.Category.HUD);
        register(keys);

        HudModule cps = hud("CPS", "Show left/right clicks per second.");
        cps.addOption(new DropdownOption("Show", 0, "Both", "Left only", "Right only"));
        cps.addOption(new BoolOption("Show label", true));

        Module titleScale = new TitleScaleModule();
        titleScale.setEnabled(true);
        register(titleScale);
        registerHidden(new ExordiumModule(), Module.Category.MECHANIC);

        TexturePacksModule tp = new TexturePacksModule();
        tp.addOption(new SliderOption("Size", 100, 50, 200, 25));
        tp.addOption(new ColorOption("Background", true, 0x80000000));
        tp.addOption(new DropdownOption("Color mode", 0, "Static", "Chroma", "Chroma Fast", "Fade"));
        tp.addOption(new BoolOption("Show Icons", true));
        tp.addOption(new BoolOption("Show All", false));
        tp.addOption(new DropdownOption("Max Packs", 2, "1", "2", "3", "4", "5", "6", "7", "8"));
        register(tp, Module.Category.HUD);

        // 1.7.0: Simple Voice Chat's HUD as Lads elements; on, they replace its fixed-position icons (26.x only: no SVC on 1.8.9).
        hud("Voice Chat", "Simple Voice Chat's talking, muted, deafened and disconnected icon.").addOption(new BoolOption("Show label", true));
        getModule("Voice Chat").setEnabled(true);
        hud("Voice Chat Group", "The players in your Simple Voice Chat group; talking players turn green.").setEnabled(true);

        // 1.9.10: no plate by default (its own fully transparent Background; ConfigManager.apply1910HudOverride sets it once).
        HudModule pot = hud("Potion Effects", "Show your active potion effects.", new ColorOption("Background", false, 0));
        pot.addOption(new BoolOption("Show icon", true));
        pot.addOption(new BoolOption("Show duration", true));
        pot.addOption(new BoolOption("Show when empty", false));
        pot.setEnabled(true);

        // Gameplay toggle modules
        FullbrightModule fb = new FullbrightModule();
        register(fb, Module.Category.MECHANIC);

        ToggleSprintModule toggleSprint = new ToggleSprintModule();
        toggleSprint.setEnabled(true);
        ((BoolOption) toggleSprint.getOption("Sprint toggled")).set(true);
        register(toggleSprint, Module.Category.MECHANIC);

        Module zoom = new ZoomModule();
        zoom.setEnabled(true);
        register(zoom, Module.Category.MECHANIC);
        Module customFov = new CustomFovModule();
        customFov.setEnabled(false);
        register(customFov, Module.Category.MECHANIC);

        Module smoothHotbar = new SmoothHotbarModule();
        smoothHotbar.setEnabled(true);
        register(smoothHotbar, Module.Category.MECHANIC);
        register(new DynamicLightsModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.CloudHeightModule(), Module.Category.MECHANIC);

        // Damage Tilt (OldDamageTilt before 1.9.10): the hurt tilt leans by the hit's direction (Directional) or the old fixed way,
        // scaled by Intensity, or none (Disable Tilt). It replaces Minecraft's own Damage Tilt slider, which 1.9.10 hides. On by
        // default since 1.7.2 (ConfigManager.migrateDamageTilt upgrades older configs). Off, each version keeps its vanilla tilt.
        Module odt = new Module(com.thelads.core.client.DamageTilt.MODULE, "Hurt camera tilt: towards the hit (Directional) or the old "
            + "fixed way. Disable Tilt turns it off.");
        odt.addOption(new BoolOption(com.thelads.core.client.DamageTilt.DISABLE, false));
        odt.addOption(new BoolOption(com.thelads.core.client.DamageTilt.DIRECTIONAL, true));
        odt.addOption(new SliderOption(com.thelads.core.client.DamageTilt.INTENSITY, 100, 0, 100, 5));
        odt.setEnabled(true);
        register(odt, Module.Category.MECHANIC);

        Module swing = new Module("LegacySwing", "Xbox 360 mining and hand swing matching the supplied Legacy Console showcase.");
        register(swing, Module.Category.MECHANIC);

        Module vb = new Module("VerticalBobbing", "Adds vertical view bob (incl. jumping/falling).");
        vb.addOption(new DropdownOption("Intensity", 1, "Low", "Normal", "High"));
        vb.setEnabled(true);
        register(vb, Module.Category.MECHANIC);
        Module oldAnim = new OldAnimationsModule();
        oldAnim.setEnabled(true);
        register(oldAnim, Module.Category.MECHANIC);
        Module itemPhysics = new ItemPhysicsModule();
        itemPhysics.setEnabled(true);
        register(itemPhysics, Module.Category.MECHANIC);
        Module wavyCapes = new WavyCapesModule();
        wavyCapes.setEnabled(true);
        register(wavyCapes, Module.Category.MECHANIC);
        Module tntTimer = new TntTimerModule();
        tntTimer.setEnabled(true);
        register(tntTimer, Module.Category.MECHANIC);

        Module dynamicFps = new DynamicFPSModule();
        dynamicFps.setEnabled(true);
        register(dynamicFps, Module.Category.MECHANIC);
        register(new ToggleNametagsModule(), Module.Category.MECHANIC);
        registerHidden(new TitleScreenModule());
        register(new PaperdollModule());
        register(new KillBannerModule(), Module.Category.MECHANIC);
        register(new ComboSoundsModule(), Module.Category.MECHANIC);
        Module crosshair = new CrosshairModule();
        crosshair.setEnabled(true);
        register(crosshair, Module.Category.MECHANIC);

        // Every chat feature lives here; ConfigManager migrates the old ClientTools timestamps and HideChatIndicators.
        Module chatMod = new com.thelads.core.modules.ChatModule("Chat size, background, message animations, timestamps, signing indicators, screenshot links and Chat Heads (the sender's head before messages).");
        chatMod.addOption(new BoolOption("Chat Background", false));
        chatMod.addOption(new SliderOption("Chat Width", 320, 100, 600, 10));
        chatMod.addOption(new SliderOption("Chat Height", 180, 50, 400, 10));
        chatMod.addOption(new BoolOption("Message Animations", true));
        chatMod.addOption(new BoolOption("Timestamps", false));
        chatMod.addOption(new BoolOption("Hide Signing Indicators", true));
        chatMod.addOption(new BoolOption("Screenshot Link Buttons", true));
        // 1.7.0, both on by default: no 100-line history cap (ChatHistory), and chat text with its shadow.
        chatMod.addOption(new BoolOption("Infinite History", true));
        chatMod.addOption(new BoolOption("Text Shadow", true));
        // 1.9.1: chat sits above the armor row of the survival HUD, so a message never covers it (ChatLift).
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatLift.OPTION, true));
        // 1.9.0: Chat Heads, once a module of its own. The sender's head in chat, just before their name or at the start of the line:
        // from signed chat, or from player names in the tab list. Keep Text Aligned applies to Start of line. The Chat module's
        // own switch does not turn it off (ChatHeads.enabled); a Chat Heads jar the player added hides these (ChatModule.hidden).
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatHeads.ENABLED, true));
        chatMod.addOption(new DropdownOption(com.thelads.core.client.ChatHeads.POSITION, 0, com.thelads.core.client.ChatHeads.POSITIONS));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatHeads.BY_NAME, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatHeads.ALIGNED, false));
        chatMod.setEnabled(true);
        register(chatMod, Module.Category.HUD);

        Module autoReconnect = new AutoReconnectModule();
        autoReconnect.setEnabled(true);
        register(autoReconnect, Module.Category.SERVER);

        Module packets = new Module("IgnorePacketErrors", "Stay connected when the server sends a packet the client can't read or handle: it is skipped and logged "
            + "instead of disconnecting with Network Protocol Error. Warning: a skipped packet can leave the world out of sync (missing blocks, "
            + "entities or inventory changes) until you rejoin, and a broken or hostile server can keep you connected; turn it off on servers you "
            + "don't trust. Timeouts, kicks and lost connections still disconnect.");
        packets.setEnabled(false);
        register(packets, Module.Category.SERVER);

        register(new DiscordRpcModule(), Module.Category.SERVER);
        registerHidden(new com.thelads.core.modules.CosmeticsModule(), Module.Category.MECHANIC); // always on: the Cosmetics screen
        // 1.9.2: the Hypixel mods grouped into one.
        register(com.thelads.core.client.hypixel.HypixelMods.module(), Module.Category.HYPIXEL);
        register(com.thelads.core.client.hypixel.quickplay.QuickplayModule.module(), Module.Category.HYPIXEL);

        HudModule sessionStats = hud("Session Stats", "Hypixel only. Track games, wins, losses, kills, deaths, win streaks, and beds broken during this session.");
        sessionStats.setEnabled(false);
        ActionOption resetSession = new ActionOption("Reset session", "Reset session");
        resetSession.confirm("Are you sure you want to reset session stats?");
        resetSession.setAction(com.thelads.core.client.hypixel.SessionTracker::reset);
        sessionStats.addOption(resetSession);

        HudModule heightLimit = hud("Bed Wars Height Limit", "Hypixel Bed Wars only. Show remaining height to the map build limit.");
        heightLimit.setEnabled(true);

        Module sb = new Module("Scoreboard", "Resize, reposition and restyle the scoreboard sidebar.");
        sb.addOption(new SliderOption("Size", 100, 50, 150, 25));
        sb.addOption(new SliderOption("X Offset", 0, -40, 40, 20));
        sb.addOption(new SliderOption("Y Offset", 0, -40, 40, 20));
        sb.addOption(new DropdownOption("Background", 3, "Default", "Dark", "Light", "Off"));
        sb.addOption(new BoolOption("Text Shadow", true));
        // 1.7.0: the scoreboard shows its own colours unless this is on (1.6.0 always tinted it with the HUD colour).
        sb.addOption(new BoolOption("Custom Text Color", false));
        sb.addOption(new BoolOption("Hide Red Numbers", false));
        sb.addOption(new BoolOption("Hide Sequential Only", true));
        sb.setEnabled(true);
        register(sb, Module.Category.HUD);

        Module tab = new TabListModule();
        tab.setEnabled(true);
        register(tab, Module.Category.HUD);


        Module betterRes = new BetterResolutionModule();
        betterRes.setEnabled(false);
        register(betterRes);

        registerHidden(new XaeroWorldMapModule(), Module.Category.SERVER);
        registerHidden(new JeiModule());

        Module sl = new Module("ScalableLux", "Highly optimized starlight lighting engine.");
        sl.setEnabled(true);
        registerHidden(sl);

        Module farBlock = new Module("FarBlockEntities", "Render block entities further away.");
        farBlock.addOption(new SliderOption("Distance", 128, 64, 256, 16));
        farBlock.setEnabled(true);
        register(farBlock);

        register(new RaisedModule());

        Module appleSkin = new AppleSkinModule();
        appleSkin.setEnabled(true);
        register(appleSkin);
        Module enhancedTooltips = new EnhancedTooltipsModule();
        enhancedTooltips.setEnabled(true);
        register(enhancedTooltips);
        Module enhancedToolbars = new EnhancedToolbarsModule();
        enhancedToolbars.setEnabled(true);
        register(enhancedToolbars);
        registerHidden(new NotEnoughAnimationsModule());
        registerHidden(new BetterStatsModule());
        registerHidden(new Module("ModernAdvancements", "Search advancements, track goals and customize advancement toasts."));
        registerHidden(new Module("Resourcify", "Browse and update resource packs, data packs, shaders and worlds."));

        Module packs = new Module(com.thelads.core.client.pack.PackMenuSettings.MODULE_NAME,
            "Resource pack menu search, folder navigation and transparent layout. Ignore incompatible packs hides packs made for "
                + "another Minecraft version from the Available list; No pack compat check loads them with no warning.");
        packs.addOption(new BoolOption(com.thelads.core.client.pack.PackMenuSettings.OPTION_TRANSPARENT, true));
        packs.addOption(new BoolOption(com.thelads.core.client.pack.PackMenuSettings.OPTION_IGNORE_INCOMPATIBLE, false));
        packs.addOption(new BoolOption(com.thelads.core.client.pack.PackMenuSettings.OPTION_NO_COMPAT_CHECK, true));
        packs.setEnabled(true);
        register(packs, Module.Category.MECHANIC);
        register(com.thelads.core.client.KeepUiOpen.module(), Module.Category.MECHANIC);

        // 1.9.10: always on and out of the menu (owner); a saved "off" no longer switches it off.
        Module disableNarrator = new Module("DisableNarrator", "Disables narrator.") {
            @Override public void setEnabled(boolean enabled) { super.setEnabled(true); }
        };
        disableNarrator.setEnabled(true);
        registerHidden(disableNarrator);

        register(new SignalLossModule());
        registerHidden(new FlashbackModule());

        Module decentScreenshot = new Module("BetterScreenshots", "Better screenshot saving and GUI.");
        decentScreenshot.setEnabled(true);
        register(decentScreenshot);

        Module clumps = new ClumpsModule();
        clumps.setEnabled(true);
        register(clumps, Module.Category.MECHANIC);
        register(new SkinLayersModule(), Module.Category.MECHANIC);
        registerHidden(new Module("Lithium", "Optimize game logic, physics and world ticking."), Module.Category.MECHANIC);
        registerHidden(new Module("FerriteCore", "Reduce memory used by Minecraft's block and model data."), Module.Category.MECHANIC);
        registerHidden(new Module("XaeroMinimap", "Minimap, waypoints and navigation controls."), Module.Category.HUD);
        register(new ShulkerBoxUtilsModule(), Module.Category.MECHANIC);
        register(new MouseTweaksModule(), Module.Category.MECHANIC);
        Module rawInput = new Module("RawInput", "Raw mouse input directly from hardware bypassing Windows acceleration.");
        rawInput.setEnabled(true);
        register(rawInput, Module.Category.MECHANIC);
        Module borderless = new Module("BorderlessFullscreen", "Borderless windowed fullscreen mode.");
        borderless.addOption(new BoolOption("True borderless", false));
        borderless.setEnabled(true);
        register(borderless, Module.Category.MECHANIC);
        register(new SurvivalTweaksModule(), Module.Category.MECHANIC);
        register(new StackRefillModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ColorSaturationModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.MotionBlurModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.MenuBlurModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.HideFoliageModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ClearGlassModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.CustomSkyModule(), Module.Category.MECHANIC);
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

    private void registerHidden(Module module) {
        modules.put(module.getName(), module);
        hiddenModuleNames.add(module.getName());
    }

    private void registerHidden(Module module, Module.Category category) {
        module.setCategory(category);
        modules.put(module.getName(), module);
        hiddenModuleNames.add(module.getName());
    }

    public boolean isHiddenModule(String name) {
        return hiddenModuleNames.contains(name);
    }

    private HudModule hud(String name, String description) {
        return hud(name, description, new ColorOption("Background", true, 0x80000000));
    }

    private HudModule hud(String name, String description, ColorOption background) {
        HudModule m = new HudModule(name, description);
        m.addOption(new SliderOption("Size", 100, 50, 200, 25));
        m.addOption(background);
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

    /**
     * Resets all registered modules and their options in-place to the shipped 1.9.7 defaults
     * while preserving existing {@link Module} object references.
     */
    public synchronized void resetToDefaults() {
        ModuleManager defaults = new ModuleManager();
        for (Map.Entry<String, Module> entry : defaults.modules.entrySet()) {
            Module defMod = entry.getValue();
            Module liveMod = modules.get(entry.getKey());
            if (liveMod == null) {
                continue;
            }
            liveMod.setEnabled(defMod.isEnabled());
            liveMod.setFavorite(false);
            liveMod.setLastModified(0L);
            if (liveMod instanceof HudModule liveHud && defMod instanceof HudModule defHud) {
                liveHud.setUseGlobalColor(defHud.isUseGlobalColor());
                liveHud.setCustomColor(defHud.getCustomColor());
            }
            for (Option defOpt : defMod.getOptions()) {
                Option liveOpt = liveMod.getOption(defOpt.getName());
                if (liveOpt != null) {
                    try {
                        liveOpt.load(defOpt.save());
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
