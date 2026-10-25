package com.thelads.core.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.thelads.core.modules.*;
import com.thelads.core.client.hud.CustomImageHudElement;
import com.thelads.core.client.hud.CustomTextHudElement;
import com.thelads.core.client.hud.HudExtras;

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
        hud("ItemCounter", "Count held items, arrows, totems, rockets, golden apples, ender pearls, potions or soups in your inventory and offhand.")
            .addOption(new DropdownOption("Item", 0, "Held item", "Arrows", "Totems", "Rockets", "Golden Apples", "Ender Pearls", "Potions", "Soups"));
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
        fps.addOption(new DropdownOption("Stats", 0, "Off", "Min / Max", "Average", "1% Low", "Full"));
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
        Module chatMod = new com.thelads.core.modules.ChatModule("Chat size, background, message animations, timestamps, signing indicators, screenshot links and Chat Heads (the sender's head before messages); stacking, tabs (Party/Guild/PM on Hypixel; Custom Tabs: Name=regex;Name=regex), search, keys, mentions, filter, Shortcuts (/l=/lobby;gg=Good game) and translation.");
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
        // parity-1023 (client ChatTweaks; each adapter hooks them): stacking, tabs, search, keys, per-server history, mentions,
        // profanity, shortcuts, message buttons and translation (ChatTranslator, off: it sends the message's text to Google Translate).
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.COMPACT, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.TABS, false));
        chatMod.addOption(new TextOption(com.thelads.core.client.ChatTweaks.CUSTOM_TABS, ""));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.SEARCH, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.BUTTONS, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.KEEP_OPEN, true));
        chatMod.addOption(new ActionOption(com.thelads.core.client.ChatTweaks.PEEK_KEY, "Controls"));
        chatMod.addOption(new ActionOption(com.thelads.core.client.ChatTweaks.TOGGLE_KEY, "Controls"));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.SERVER_HISTORY, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.MENTIONS, true));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.MENTION_SOUND, false));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.PROFANITY, false));
        chatMod.addOption(new TextOption(com.thelads.core.client.ChatTweaks.SHORTCUTS, ""));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.TRANSLATE, false));
        chatMod.addOption(new DropdownOption(com.thelads.core.client.ChatTweaks.TRANSLATE_TO, 0, com.thelads.core.client.ChatTranslator.NAMES));
        chatMod.addOption(new BoolOption(com.thelads.core.client.ChatTweaks.FUZZY, true));
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
        Module social = new Module(com.thelads.core.client.social.SocialState.MODULE, "Friends, what they are playing, messages, invites and "
            + "joining each other's worlds peer to peer, through the Lads social server (never the game's connection). Your Minecraft name and "
            + "UUID go to that server. Off: no connection to it at all.");
        social.setEnabled(true);
        register(social, Module.Category.SERVER);
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
        register(new LadsBadgeModule(), Module.Category.HUD);


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
        packs.addOption(new BoolOption(com.thelads.core.client.pack.PackMenuSettings.OPTION_PROFILES, true)); // Profiles button: named pack sets
        packs.setEnabled(true);
        register(packs, Module.Category.MECHANIC);
        register(com.thelads.core.client.KeepUiOpen.module(), Module.Category.MECHANIC);

        // 1.9.10: always on and out of the menu (owner); a saved "off" no longer switches it off.
        Module disableNarrator = new Module("DisableNarrator", "Disables narrator.") {
            @Override public void setEnabled(boolean enabled) { super.setEnabled(true); }
        };
        disableNarrator.setEnabled(true);
        registerHidden(disableNarrator);

        Module serverControl = new Module("Server Control", "Server control permissions and notifications.") {
            @Override public void setEnabled(boolean enabled) { super.setEnabled(true); }
        };
        serverControl.addOption(new BoolOption("Let servers change my Lads settings", true));
        serverControl.addOption(new BoolOption("Show server notice", true));
        serverControl.setEnabled(true);
        register(serverControl, Module.Category.SERVER); // visible: the player must be able to turn "Let servers change" off

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
        // PvP camera and world visuals (parity-1023, all off by default; Freelook and Fog Customizer's see-through options are gated).
        register(new FreelookModule(), Module.Category.MECHANIC);
        register(new ViewModelModule(), Module.Category.MECHANIC);
        register(new TimeChangerModule(), Module.Category.MECHANIC);
        register(new WeatherChangerModule(), Module.Category.MECHANIC);
        register(new FogCustomizerModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.LogPrunerModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.NumericalEnchantmentsModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ConfirmDisconnectModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.DropConfirmationModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ScrollTweaksModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.AutoPerspectiveModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.AutoBackupModule(), Module.Category.MECHANIC);
        // PvP visuals (client PvpVisuals; each adapter hooks them): all off by default, visual only but Hitboxes' Through Walls.
        register(new BlockOverlayModule(), Module.Category.MECHANIC);
        register(new HitColorModule(), Module.Category.MECHANIC);
        register(new DamageTintModule(), Module.Category.MECHANIC);
        register(new GlintColorizerModule(), Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.PvpVisuals.HIDE_MOB_DEATHS, "Dying mobs vanish at once instead of tipping over red. "
            + "Players still show their death."), Module.Category.MECHANIC);
        register(new MountOpacityModule(), Module.Category.MECHANIC);
        register(new HitboxesModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ColorSaturationModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.MotionBlurModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.MenuBlurModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.HideFoliageModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ClearGlassModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.CustomSkyModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.CustomItemTexturesModule(), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.ChunkAnimatorModule(), Module.Category.MECHANIC);

        // PvP visuals (PvpVisuals reads these once a tick). On with vanilla's look, so each option works as soon as it is set.
        Module renderTweaks = new Module(com.thelads.core.client.PvpVisuals.RENDER_TWEAKS, "Entity shadows, lightning bolts, fishing lines and "
            + "the Elder Guardian curse. Bobbing Disconnect stops the world bobbing while your hand still bobs.");
        renderTweaks.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.SHADOWS, true));
        renderTweaks.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.HIDE_LIGHTNING, false));
        renderTweaks.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.HIDE_FISHING_LINE, false));
        renderTweaks.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.HIDE_ELDER_GUARDIAN, false));
        renderTweaks.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.BOBBING_DISCONNECT, false));
        renderTweaks.setEnabled(true);
        register(renderTweaks, Module.Category.MECHANIC);

        Module overlays = new Module(com.thelads.core.client.PvpVisuals.OVERLAYS, "How much the fire, carved pumpkin and nether portal overlays "
            + "cover your screen. Low Fire is in 1.7 Animations.");
        overlays.addOption(new SliderOption(com.thelads.core.client.PvpVisuals.FIRE_OPACITY, 100, 0, 100, 5).percent());
        overlays.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.FIRE_RESISTANCE, false));
        overlays.addOption(new SliderOption(com.thelads.core.client.PvpVisuals.PUMPKIN_OPACITY, 100, 0, 100, 5).percent());
        overlays.addOption(new SliderOption(com.thelads.core.client.PvpVisuals.PORTAL_OPACITY, 100, 0, 100, 5).percent());
        overlays.setEnabled(true);
        register(overlays, Module.Category.MECHANIC);

        Module totem = new Module(com.thelads.core.client.PvpVisuals.TOTEM, "The Totem of Undying pop animation: hide it or change its size.");
        totem.addOption(new BoolOption(com.thelads.core.client.PvpVisuals.HIDE_TOTEM, false));
        totem.addOption(new SliderOption(com.thelads.core.client.PvpVisuals.TOTEM_SCALE, 100, 25, 200, 5).percent());
        totem.setEnabled(true);
        register(totem, Module.Category.MECHANIC);

        Module darkMode = new Module(com.thelads.core.client.PvpVisuals.DARK_MODE, "Darkens chest, inventory and other container screens; their "
            + "titles turn light.");
        darkMode.addOption(new SliderOption(com.thelads.core.client.PvpVisuals.DARKNESS, 60, 30, 90, 5).percent());
        register(darkMode, Module.Category.MECHANIC);

        // Other players' health is not on vanilla's name tags: HypixelSafetyManager gates it (risky set).
        register(new Module(com.thelads.core.client.PvpVisuals.NAMETAG_HEALTH, "Shows each player's health after their name tag. "
            + "Off on servers that don't allow it."), Module.Category.MECHANIC);
        // PARITY-1023 pvp (rows 31-38): Light Overlay, Loot Beams, Mob Overlay and Mob Size are risky (HypixelSafetyManager), off by default.
        register(new OofModule(), Module.Category.MECHANIC);
        register(com.thelads.core.client.LightOverlay.create(), Module.Category.MECHANIC);
        register(new LootBeamsModule(), Module.Category.MECHANIC);
        register(new MobOverlayModule(), Module.Category.MECHANIC);
        register(new MobSizeModule(), Module.Category.MECHANIC);
        register(new ItemDespawnModule(), Module.Category.MECHANIC);
        register(new UhcOverlayModule(), Module.Category.MECHANIC);
        register(new NickHiderModule(), Module.Category.MECHANIC);
        register(new ContainerQoLModule(), Module.Category.MECHANIC);
        HudModule invHud = hud("InventoryHUD", "Display your own inventory contents on screen.");
        invHud.setEnabled(false);

        var combo = hud("ComboCounter", "Displays consecutive melee hits landed on opponents.");
        combo.addOption(new DropdownOption("Display", 0, "Combo: X", "X Hits", "Number only"));
        combo.addOption(new BoolOption("Hide when zero", false));

        var pvpInfo = hud("PvPInfo", "Summary of PvP combat statistics: accuracy, hits landed, hits taken, gapples used.");
        pvpInfo.addOption(new BoolOption("Show accuracy", true));
        pvpInfo.addOption(new BoolOption("Show hits landed", true));
        pvpInfo.addOption(new BoolOption("Show hits taken", true));
        pvpInfo.addOption(new BoolOption("Show gapples", true));
        pvpInfo.addOption(new DropdownOption("Layout", 0, "Multi-line", "Compact"));

        var potCounter = hud("PotionCounter", "Count splash healing potions and soups in your inventory.");
        potCounter.addOption(new DropdownOption("Display", 0, "Both", "Potions only", "Soups only"));

        var itemTracker = hud("ItemTracker", "Display recent item pickup and drop history notifications on screen.");
        itemTracker.addOption(new SliderOption("Max entries", 4, 1, 8, 1));
        itemTracker.addOption(new SliderOption("Duration (seconds)", 3, 1, 8, 1));
        itemTracker.addOption(new BoolOption("Show pickups", true));
        itemTracker.addOption(new BoolOption("Show drops", true));

        var cooldowns = hud("Cooldowns", "Display active item cooldown timers and progress on the HUD.");
        cooldowns.addOption(new DropdownOption("Display mode", 0, "Text & Time", "Bar & Time", "Compact"));
        cooldowns.addOption(new BoolOption("Include pearls", true));

        var tps = hud("TPS", "Displays estimated server ticks per second (TPS).");
        tps.addOption(new DropdownOption("Format", 0, "20.0 TPS", "TPS: 20.0", "Number only"));
        tps.addOption(new SliderOption("Decimals", 1, 0, 2, 1));

        var sysRes = hud("SystemResources", "Displays real-time CPU, RAM, and system resource utilization.");
        sysRes.addOption(new BoolOption("Show CPU", true));
        sysRes.addOption(new BoolOption("Show RAM", true));
        sysRes.addOption(new DropdownOption("Layout", 0, "Single line", "Multi-line"));

        var playtime = hud("Playtime", "Displays elapsed playtime during your session or in the current world.");
        playtime.addOption(new DropdownOption("Format", 0, "HH:mm:ss", "Words (1h 24m)", "Minutes"));
        playtime.addOption(new DropdownOption("Prefix", 0, "Playtime: ", "Session: ", "Uptime: ", "None"));

        var compass = hud("CompassStrip", "Displays a scrolling navigational compass ribbon that rotates with the player.");
        compass.addOption(new SliderOption("Width", 140, 80, 240, 10));
        compass.addOption(new BoolOption("Show degrees", true));
        compass.addOption(new BoolOption("Center notch", true));

        var viewAngles = hud("ViewAngles", "Displays precise camera yaw and pitch degrees.");
        viewAngles.addOption(new DropdownOption("Format", 0, "Yaw and Pitch", "Short (Y/P)", "Compact (X, Y)", "Yaw only", "Pitch only"));
        viewAngles.addOption(new SliderOption("Decimals", 1, 0, 2, 1));

        var mouseTrail = hud("MouseTrail", "Displays an animated graphical trail tracking mouse movement.");
        mouseTrail.addOption(new SliderOption("Box size", 50, 30, 80, 5));
        mouseTrail.addOption(new SliderOption("Trail lifetime (ms)", 350, 100, 1000, 50));
        mouseTrail.addOption(new SliderOption("Sensitivity", 100, 50, 200, 10));
        mouseTrail.addOption(new BoolOption("Center dot", true));

        var blockCounter = hud("BlockCounter", "Tracks blocks placed in this session and blocks remaining in inventory.");
        blockCounter.addOption(new DropdownOption("Mode", 0, "Placed blocks", "Remaining held", "Both"));
        blockCounter.addOption(new DropdownOption("Prefix", 0, "Placed: ", "Blocks: ", "None"));

        var protection = hud("Protection", "Displays active defensive enchantments (Protection, Fire Prot, Feather Falling) on equipped gear.");
        protection.addOption(new DropdownOption("Display", 0, "Summary", "All defensive", "Protection only"));
        protection.addOption(new BoolOption("Include Feather Falling", true));
        protection.addOption(new BoolOption("Hide when zero", false));

        var deathInfo = hud("DeathInfo", "Displays total deaths, last death coordinates, and death cause.");
        deathInfo.addOption(new DropdownOption("Display", 0, "Deaths & Coords", "Deaths only", "Last Coords", "Full"));
        deathInfo.addOption(new BoolOption("Show dimension", false));

        var hearts = hud("Hearts", "Custom hearts display with condensed absorption rows and armor/toughness points.");
        hearts.addOption(new BoolOption("Show armor", true));
        hearts.addOption(new BoolOption("Show toughness", true));
        hearts.addOption(new BoolOption("Condense absorption", true));

        var hitIndicator = hud("HitIndicator", "Visual directional arc around your crosshair indicating incoming damage direction.");
        hitIndicator.addOption(new SliderOption("Radius", 40, 20, 80, 5));
        hitIndicator.addOption(new SliderOption("Duration (ms)", 800, 200, 2000, 100));
        hitIndicator.addOption(new SliderOption("Arc Size", 20, 10, 40, 2)); // not "Size": hud() adds the HUD Size under that name
        // HUD widgets and vanilla HUD tweaks (parity 1.9.19, common HudExtras; each version's HudExtras tick feeds them). All off by
        // default; Damage Indicator, Horse Stats, Player Counter and Totem Pop Counter show what vanilla does not and are gated.
        HudModule damage = hud(HudExtras.DAMAGE_INDICATOR, "The health of the mob or player you look at, and floating damage numbers.");
        damage.addOption(new DropdownOption("Display", 0, "Both", "Target HUD", "Damage Numbers"));
        damage.addOption(new DropdownOption("Health Format", 0, "Hearts", "Points", "Percent"));
        damage.addOption(new BoolOption("Health Bar", true));
        damage.addOption(new SliderOption("Number Duration", 1000, 400, 3000, 100).suffix(" ms"));
        HudModule jump = hud(HudExtras.JUMP_RESET, "Rates your jump against each hit's knockback: Perfect on the hit's tick, Early, Late or Missed.");
        jump.addOption(new BoolOption("Show Timing", true));
        jump.addOption(new BoolOption("Show Streak", true));
        HudModule media = hud(HudExtras.MEDIA, "What Windows plays now (Spotify, browsers, any app with media controls): title, artist and progress.");
        media.addOption(new BoolOption("Show Artist", true));
        media.addOption(new DropdownOption("Progress", 3, "Off", "Bar", "Time", "Both"));
        media.addOption(new BoolOption("Hide When Paused", false));
        HudModule horse = hud(HudExtras.HORSE, "Jump height, speed and health of the horse you ride or look at.");
        horse.addOption(new BoolOption("Show Jump", true));
        horse.addOption(new BoolOption("Show Speed", true));
        horse.addOption(new BoolOption("Show Health", true));
        horse.addOption(new DropdownOption("Speed Unit", 0, "Blocks/s", "km/h"));
        HudModule counter = hud(HudExtras.PLAYERS, "Players on the server and other players near you.");
        counter.addOption(new DropdownOption("Display", 0, "Both", "Nearby", "Server"));
        counter.addOption(new StringOption("Prefix", "Players: "));
        HudModule totems = hud(HudExtras.TOTEMS, "How many totems of undying each player near you has used.");
        totems.addOption(new SliderOption("Duration", 30, 5, 300, 5).suffix(" s"));
        totems.addOption(new BoolOption("Reset On Death", true));
        totems.addOption(new BoolOption("Chat Message", false));
        HudModule block = hud(HudExtras.BLOCK_INFO, "The block you look at: the tool it needs, whether you can harvest it, your mining progress and its light.");
        block.addOption(new BoolOption("Show Tool", true));
        block.addOption(new BoolOption("Show Harvestability", true));
        block.addOption(new BoolOption("Show Mining Progress", true));
        block.addOption(new BoolOption("Show Light Level", true));
        hud(HudExtras.CUSTOM_TEXT, "Your own text; {player} {fps} {ping} {x} {y} {z} {biome} {direction} {server} {time} {day} {speed} {memory} "
            + "are filled in, \\n starts a line.").addOption(new StringOption("Text", CustomTextHudElement.DEFAULT));
        HudModule shape = hud(HudExtras.CUSTOM_SHAPE, "A rectangle of your size and colours, to frame or back other HUDs.");
        shape.addOption(new SliderOption("Width", 100, 2, 500, 1));
        shape.addOption(new SliderOption("Height", 40, 2, 300, 1));
        shape.addOption(new SliderOption("Border Thickness", 1, 0, 10, 1));
        shape.addOption(new ColorOption("Fill Color", false, 0x80000000));
        shape.addOption(new ColorOption("Border Color", false, 0xFFFFFFFF));
        HudModule image = hud(HudExtras.CUSTOM_IMAGE, "A PNG or JPG picture you choose, at your width.");
        image.addOption(new TextOption("Image", ""));
        image.addOption(new ActionOption("Choose Image", "Choose image...")).setAction(CustomImageHudElement::choose);
        image.addOption(new SliderOption("Width", 64, 8, 400, 1));
        hud(HudExtras.HEAD, "Your skin's face.");
        hud(HudExtras.ABOVE, "The first block over your head and how many blocks of air are between.").addOption(new BoolOption("Show Distance", true));
        hud(HudExtras.ENTITIES, "How many entities are loaded around you (F3's count).");
        hud(HudExtras.SHADER, "The shader pack in use, or Off.");
        hud(HudExtras.LORE, "The enchantments and lore of the item in your hand.");
        register(new SubtitlesModule(), Module.Category.HUD);
        register(new TitleTweakerModule(), Module.Category.HUD);
        register(new ToastControlModule(), Module.Category.HUD);
        register(new VanillaHudModule(), Module.Category.HUD);
        // parity-1023 chat lane (client ChatTweaks / InventoryTweaks). Gated by HypixelSafetyManager (risky set), off by default:
        // Unlimited Command Length, Auto Text, Inventory Move and Armor Swap.
        register(new Module(com.thelads.core.client.ChatTweaks.LONG_COMMANDS, "Type commands longer than 256 characters (up to 32767) in "
            + "singleplayer, where the game's own server takes them. Multiplayer servers keep vanilla's limit."), Module.Category.MECHANIC);
        register(new com.thelads.core.modules.AutoTextModule(), Module.Category.MECHANIC);
        Module slotLock = new Module(com.thelads.core.client.InventoryTweaks.SLOT_LOCK, "Point at one of your inventory slots and press the "
            + "Lock key: its item can't be dropped, picked up, swapped or shift-clicked out until you unlock it. Locked Slots lists them "
            + "(hotbar 0-8, inventory 9-35, armor 36-39, offhand 40).");
        slotLock.addOption(new ActionOption(com.thelads.core.client.InventoryTweaks.LOCK_KEY, "Controls"));
        slotLock.addOption(new TextOption(com.thelads.core.client.InventoryTweaks.LOCKED, ""));
        register(slotLock, Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.InventoryTweaks.SEARCH, "A search box on chests and inventories: stacks whose name "
            + "doesn't match are dimmed."), Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.InventoryTweaks.MOVE, "Walk and jump while a chest or inventory is open. "
            + "Off on servers that don't allow it or whose rules are unknown."), Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.InventoryTweaks.ARMOR_SWAP, "Middle-click armor in your inventory to wear it, swapping "
            + "what you had on. Off on servers that don't allow it or whose rules are unknown."), Module.Category.MECHANIC);
        // Parity 1.9.19 utility rows 42, 44, 45, 41 (pack profiles are an option on ResourcePacks above).
        register(new SurvivalGuardsModule(), Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.BreedingTimer.NAME,
            "The time left before an animal you saw breed can breed again, over the animal."), Module.Category.MECHANIC);
        register(new Module(com.thelads.core.client.EnderChestPeek.NAME,
            "Hover an ender chest item to see what your ender chest held when you last closed it on this server."), Module.Category.MECHANIC);
        Module drag = new Module(com.thelads.core.client.ServerListDrag.NAME,
            "Drag saved servers up and down the multiplayer list to reorder them; the new order is saved.");
        drag.setEnabled(true);
        register(drag, Module.Category.MECHANIC);
        // Parity 1.9.19 chat lane rows 20, 25, 28, 29, 33, 35, 36 (common client TooltipScroll, CrashCatcher, QuickReconnect, KeepCursor,
        // SoundVolumes, GuiScaleOverride, ChunkBorders; each version's Qol class hooks them). All visual only.
        Module tooltips = new Module(com.thelads.core.client.TooltipScroll.NAME, "Scroll tooltips taller than the screen with the mouse "
            + "wheel, and wider ones sideways with Shift held.");
        tooltips.addOption(new SliderOption(com.thelads.core.client.TooltipScroll.SPEED, 3, 1, 10, 1));
        tooltips.addOption(new BoolOption(com.thelads.core.client.TooltipScroll.INVERT, false));
        tooltips.addOption(new BoolOption(com.thelads.core.client.TooltipScroll.SIDEWAYS, true));
        tooltips.setEnabled(true);
        register(tooltips, Module.Category.MECHANIC);
        Module crashes = new Module(com.thelads.core.client.CrashCatcher.NAME, "When the game would crash while you play in a world, leave "
            + "the world and show what crashed instead of closing the game. The crash report is still saved.");
        crashes.addOption(new SliderOption(com.thelads.core.client.CrashCatcher.LIMIT, 3, 1, 10, 1));
        crashes.setEnabled(true);
        register(crashes, Module.Category.MECHANIC);
        Module rejoin = new Module(com.thelads.core.client.QuickReconnect.NAME, "A Reconnect button when a server disconnects you, and "
            + "Direct Connect shows the typed server's MOTD, players and ping.");
        rejoin.addOption(new BoolOption(com.thelads.core.client.QuickReconnect.BUTTON, true));
        rejoin.addOption(new BoolOption(com.thelads.core.client.QuickReconnect.PING, true));
        rejoin.setEnabled(true);
        register(rejoin, Module.Category.SERVER);
        Module cursor = new Module(com.thelads.core.client.KeepCursor.NAME, "When a server swaps one chest or menu for another, the "
            + "pointer stays where it was instead of jumping to the middle.");
        cursor.setEnabled(true);
        register(cursor, Module.Category.MECHANIC);
        Module sounds = new Module(com.thelads.core.client.SoundVolumes.NAME, "A volume slider (0-200%) for each kind of game sound: "
            + "explosions, note blocks, rain, footsteps and more. Search a sound's name in the settings search.");
        for (com.thelads.core.client.SoundVolumes.Group group : com.thelads.core.client.SoundVolumes.GROUPS)
            sounds.addOption(new SliderOption(group.label(), 100, 0, 200, 5).percent());
        sounds.setEnabled(true);
        register(sounds, Module.Category.MECHANIC);
        Module guiScale = new Module(com.thelads.core.client.GuiScaleOverride.NAME, "A GUI scale between the game's whole steps, "
            + "such as 2.5x, for the HUD and every menu.");
        guiScale.addOption(new SliderOption(com.thelads.core.client.GuiScaleOverride.SCALE, 2.5, 1, 6, 0.25).suffix("x"));
        register(guiScale, Module.Category.MECHANIC);
        Module chunks = new Module(com.thelads.core.client.ChunkBorders.NAME, "F3+G's chunk borders in your colours, with a grid on your "
            + "chunk's walls and a line at every 16-block section.");
        chunks.addOption(new ColorOption(com.thelads.core.client.ChunkBorders.CORNER, false, 0xFFFFFF00));
        chunks.addOption(new ColorOption(com.thelads.core.client.ChunkBorders.NEIGHBOR, false, 0xFFFF0000));
        chunks.addOption(new BoolOption(com.thelads.core.client.ChunkBorders.GRID, true));
        chunks.addOption(new ColorOption(com.thelads.core.client.ChunkBorders.GRID_COLOR, false, 0x80FFFF00));
        chunks.addOption(new SliderOption(com.thelads.core.client.ChunkBorders.SPACING, 2, 1, 8, 1));
        chunks.addOption(new ColorOption(com.thelads.core.client.ChunkBorders.SECTION, false, 0xFF4040FF));
        register(chunks, Module.Category.MECHANIC);
        register(new BedColorsModule(), Module.Category.MECHANIC);
        register(new MlgCobwebModule(), Module.Category.MECHANIC);
        register(com.thelads.core.client.SoundFilters.createModule(), Module.Category.MECHANIC);
        register(com.thelads.core.client.media.MumbleLink.createModule(), Module.Category.MECHANIC);
        register(com.thelads.core.client.worldedit.WorldEditCUI.createModule(), Module.Category.MECHANIC);
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