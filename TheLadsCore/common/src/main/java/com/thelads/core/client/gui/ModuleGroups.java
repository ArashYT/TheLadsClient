package com.thelads.core.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Where each Lads module sits in the menu: its category, its fold-out group there, and the modules that share one row. */
public final class ModuleGroups {
    public record Group(String category, String title, List<String> members) {
        /** Fold state key; a category with one group has no fold to remember. */
        public String key() { return category + "/" + title; }
    }
    /** Modules shown as one row whose settings page has a tab for each. They stay separate modules: saved settings, Hypixel rules and server policies go by module name. */
    public record Bundle(String title, List<String> members) {}

    public static final String HOME = "Home", ALL = "All", HUD = "HUD", PERFORMANCE = "Performance", SERVER = "Server", HYPIXEL = "Hypixel", CLIENT = "Client", OTHER = "Other";
    public static final String TITLE_SCREEN = "TitleScreen";
    public static final List<String> CATEGORIES = List.of(HOME, ALL, HUD, "Combat", "Visuals", "Inventory", "Camera & Input", "Sound", CLIENT, PERFORMANCE, SERVER, HYPIXEL);
    private static final Map<String, String> ICONS = Map.ofEntries(Map.entry(HOME, "home"), Map.entry(ALL, "folders"), Map.entry(HUD, "layout"),
        Map.entry("Combat", "trophy"), Map.entry("Visuals", "eye"), Map.entry("Inventory", "box"), Map.entry("Camera & Input", "camera"), Map.entry("Sound", "volume"),
        Map.entry(CLIENT, "computer"), Map.entry(PERFORMANCE, "bolt"), Map.entry(SERVER, "server"), Map.entry(HYPIXEL, "globe"));
    private static final Map<String, String> SHORT = Map.of("Inventory", "Items", "Camera & Input", "Camera", PERFORMANCE, "Perf");
    /** Home's first rows while the player has no favorites or recent modules yet. */
    public static final List<String> POPULAR = List.of("Zoom", "Fullbright", "Toggle Sprint & Sneak", "Keystrokes", "FPS", "Coordinates", "Crosshair Tweaks", "Minimap", "KillBanner");

    public static final List<Group> GROUPS = List.of(
        new Group(HUD, "Player", List.of("Health", "Hunger", "XP", "Hearts", "ArmorHUD", "Protection", "Potion Effects", "PotionCounter", "Paperdoll", "PlayerHead", "InventoryHUD", "DeathInfo", "HorseStats")),
        new Group(HUD, "Position & Maps", List.of("Coordinates", "Direction", "CompassStrip", "Biome", "ViewAngles", "PortalCoordinates", "Speed", "BlockInfo", "BlockAbove", "Bed Wars Height Limit", "Minimap", "XaeroMinimap", "DungeonMap")),
        new Group(HUD, "Time", List.of("Clock", "Stopwatch", "Day", "Time", "Playtime", "Session Stats", "SkyBlockTimers", "Cooldowns")),
        new Group(HUD, "System & Server", List.of("FPS", "TPS", "PingHUD", "Memory", "SystemResources", "ServerAddress", "PlayerCounter", "EntityCounter", "ShaderPack", "TexturePacks")),
        new Group(HUD, "PvP", List.of("Keystrokes", "CPS", "ReachDisplay", "ComboCounter", "PvPInfo", "HitIndicator", "DamageIndicator", "JumpReset", "TotemPopCounter", "MouseTrail")),
        new Group(HUD, "Items", List.of("ItemCounter", "ItemTracker", "BlockCounter", "HeldItemLore")),
        new Group(HUD, "Game Screens", List.of("Chat", "Scoreboard", "TabList", "BossBar", "Action Bar", "Subtitles", "TitleTweaker", "ToastControl", "VanillaHUD", "Jade", "Autohide", "Lads Badge")),
        new Group(HUD, "Custom & Media", List.of("CustomText", "CustomShape", "CustomImage", "MediaPlayer", "Voice Chat", "Voice Chat Group")),
        new Group("Combat", "Feedback", List.of("KillBanner", "Hit Color", "Damage Tint", "Damage Tilt", "Totem Animation", "Hide Mob Deaths")),
        new Group("Combat", "Aim & Targets", List.of("Crosshair Tweaks", "Block Overlay", "Hitboxes", "Mob Overlay", "Mob Size", "Nametag Health")),
        new Group("Combat", "Animations", List.of("1.7 Animations", "LegacySwing", "NotEnoughAnimations")),
        new Group("Combat", "Game Modes", List.of("Bed Colors", "MLG Cobweb", "TNT Timer", "UHC Overlay")),
        new Group("Visuals", "Light & Color", List.of("Fullbright", "ColorSaturation", "DynamicLights", "Light Overlay", "Glint Colorizer", "Dark Mode")),
        new Group("Visuals", "World", List.of("Time Changer", "Weather Changer", "Fog Customizer", "Cloud Height", "CustomSky", "HideFoliage", "ClearGlass", "ChunkAnimator", "Chunk Borders")),
        new Group("Visuals", "Screen Effects", List.of("MotionBlur", "MenuBlur", "Overlays", "Render Tweaks", "Raised")),
        new Group("Visuals", "Dropped Items", List.of("Item Physics", "Loot Beams", "Item Despawn", "Custom Item Textures")),
        new Group("Visuals", "Players & Mobs", List.of("Nametags", "SkinLayers", "Wavy Capes", "Cosmetics", "Nick Hider", "Mount Opacity")),
        new Group("Inventory", "Inventory", List.of("Inventory Search", "Inventory Move", "Armor Swap", "Slot Lock", "StackRefill", "Elytra Swap", "DropConfirmation", "Survival Guards")),
        new Group("Inventory", "Containers", List.of("Container QoL", "Container Sorting", "MouseTweaks", "ShulkerBoxUtils", "Ender Chest Peek", "Keep Cursor Position", "KeepUIOpen")),
        new Group("Inventory", "Tooltips & Info", List.of("EnhancedTooltips", "EnhancedToolbars", "Scrollable Tooltips", "AppleSkin", "NumericalEnchantments", "JEI (Just Enough Items)")),
        new Group("Camera & Input", "Camera", List.of("Zoom", "Custom FOV", "Freelook", "ViewModel", "AutoPerspective", "VerticalBobbing")),
        new Group("Camera & Input", "Input", List.of("Toggle Sprint & Sneak", "ScrollTweaks", "RawInput", "SmoothHotbar", "Creative Fly Boost", "Auto Text")),
        new Group("Sound", "Sound", List.of("Sound Volumes", "Sound Filters", "Combo Sounds", "Oof Mod", "DisableNarrator", "Mumble Link")),
        new Group(CLIENT, "Menus & Window", List.of(TITLE_SCREEN, "BorderlessFullscreen", "GUI Scale", "ConfirmDisconnect", "Draggable Server List", "ResourcePacks", "Resourcify", "BetterF3", "BetterStats", "ModernAdvancements", "Link Preview", "Unlimited Command Length")),
        new Group(CLIENT, "Tools", List.of("ClientTools", "SurvivalTweaks", "Breeding Timer", "Schematic Visualizer", "WorldEdit CUI", "BetterScreenshots", "Flashback Settings")),
        new Group(CLIENT, "Files & Safety", List.of("AutoBackup", "LogPruner", "Crash Catcher")),
        new Group(PERFORMANCE, PERFORMANCE, List.of("Performance", "DynamicFPS", "UnfocusedFpsCap", "Exordium", "BetterResolution", "ScalableLux", "Clumps", "FarBlockEntities", "EntityCulling", "Lithium", "FerriteCore", "Async", "Particle Core", "Threads", "Jasione", "OptiFine189")),
        new Group(SERVER, SERVER, List.of("PingView", "AutoReconnect", "Quick Reconnect", "SignalLoss", "IgnorePacketErrors", "DiscordRPC", "Friends & Social", "Server Control", "Server Notification Actions", "XaeroWorldmap")),
        new Group(HYPIXEL, HYPIXEL, List.of("HYPIXEL Mods", "Quickplay", "Nick Detector", "SkyBlock Suite")));

    public static final List<Bundle> BUNDLES = List.of(
        new Bundle("Damage Effects", List.of("Hit Color", "Damage Tint", "Damage Tilt")),
        new Bundle("Time & Weather", List.of("Time Changer", "Weather Changer")),
        new Bundle("Old Animations", List.of("1.7 Animations", "LegacySwing")));

    private static final Map<String, Group> GROUP_OF = new HashMap<>();
    private static final Map<String, Bundle> BUNDLE_OF = new HashMap<>();
    static {
        for (Group group : GROUPS) for (String member : group.members()) GROUP_OF.put(member, group);
        for (Bundle bundle : BUNDLES) for (String member : bundle.members()) BUNDLE_OF.put(member, bundle);
    }
    /** Groups whose fold is the opposite of their default (saved with the config). */
    private static final Set<String> FLIPPED = new HashSet<>();

    private ModuleGroups() {}

    public static Group groupOf(String module) { return GROUP_OF.get(module); }
    public static Bundle bundleOf(String module) { return BUNDLE_OF.get(module); }
    public static String icon(String category) { return ICONS.get(category); }
    public static String shortLabel(String category) { return SHORT.getOrDefault(category, category); }
    /** What the player reads for a module whose saved name is not a word. */
    public static String label(String module) { return TITLE_SCREEN.equals(module) ? "Title Screen" : module; }
    /** A module's category; null when no group lists it, and the caller decides by its own category. */
    public static String categoryOf(String module) {
        Group group = groupOf(module);
        return group == null ? null : group.category();
    }
    public static List<Group> groupsIn(String category) {
        List<Group> found = new ArrayList<>();
        for (Group group : GROUPS) if (group.category().equals(category)) found.add(group);
        return found;
    }
    public static boolean isOpen(String key, boolean byDefault) { return FLIPPED.contains(key) != byDefault; }
    public static void toggle(String key) { if (!FLIPPED.remove(key)) FLIPPED.add(key); }
    public static Set<String> flipped() { return Set.copyOf(FLIPPED); }
    public static void setFlipped(Iterable<String> keys) {
        FLIPPED.clear();
        for (String key : keys) FLIPPED.add(key);
    }
}
