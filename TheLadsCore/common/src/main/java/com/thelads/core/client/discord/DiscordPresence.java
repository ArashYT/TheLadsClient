package com.thelads.core.client.discord;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Immutable, privacy-filtered data passed from Minecraft's thread to the IPC worker. */
public record DiscordPresence(String details, String state, long startTimestamp) {
    public enum Place { MENU, SINGLEPLAYER, MULTIPLAYER }
    public record Privacy(boolean serverAddress, boolean worldName, boolean dimension, boolean elapsed, int detailLevel) {}
    /** screen: what {@link #screen} calls the open screen, or null when none is open. */
    public record Game(Place place, String version, String serverAddress, String worldName, String dimension, String screen, long started) {}

    /** Detail levels: 0 Full (screens, and the server, world and dimension when shared), 1 Simple (menus or which mode), 2 Minimal. */
    public static DiscordPresence from(Game game, Privacy privacy) {
        String version = "Minecraft " + game.version();
        long started = privacy.elapsed() ? Math.max(0, game.started()) : 0;
        if (privacy.detailLevel() == 2) return new DiscordPresence("Playing Minecraft", text(version), started);
        boolean full = privacy.detailLevel() == 0;
        String screen = full ? game.screen() : null;
        String details, state;
        if (game.place() == Place.MENU) {
            details = screen != null ? screen : "In the menus";
            state = version;
        } else {
            String host = full && privacy.serverAddress() ? host(game.serverAddress()) : null;
            details = game.place() == Place.SINGLEPLAYER
                ? full && privacy.worldName() && !blank(game.worldName()) ? "Playing in " + game.worldName() : "Playing singleplayer"
                : host != null ? "Playing on " + host : "Playing multiplayer";
            state = screen != null ? screen : full && privacy.dimension() && !blank(game.dimension()) ? dimension(game.dimension()) : version;
        }
        return new DiscordPresence(text(details), text(state), started);
    }

    /** A server's host name without its port; null for IP addresses, localhost and LAN names, which can identify a home. */
    static String host(String address) {
        if (blank(address)) return null;
        String host = address.trim().toLowerCase(java.util.Locale.ROOT).replaceFirst(":\\d+$", "");
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.matches("([a-z0-9-]+\\.)+[a-z][a-z0-9-]*") && !host.endsWith(".local") && !host.endsWith(".lan") ? host : null;
    }

    static String dimension(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        switch (path) {
            case "overworld": return "In the Overworld";
            case "the_nether": return "In the Nether";
            case "the_end": return "In the End";
            default: return "In " + path.replace('_', ' ');
        }
    }

    // What Discord shows for a screen, by simple class name (a trailing version like 26 or 189 dropped): Mojang names for 26.x,
    // MCP names for 1.8.9, Lads screens, and mod-loader screens. A screen not listed takes its nearest listed superclass's.
    private static final String[][] SCREENS = {
        {"In the main menu", "TitleScreen", "GuiMainMenu"},
        {"Browsing servers", "JoinMultiplayerScreen", "GuiMultiplayer"},
        {"Adding a server", "ManageServerScreen", "EditServerScreen", "GuiScreenAddServer"},
        {"Joining a server", "DirectJoinServerScreen", "GuiScreenServerList"},
        {"Choosing a world", "SelectWorldScreen", "GuiSelectWorld"},
        {"Creating a world", "CreateWorldScreen", "GuiCreateWorld", "CreateFlatWorldScreen", "GuiCreateFlatWorld", "PresetFlatWorldScreen",
            "GuiFlatPresets", "CreateBuffetWorldScreen", "GuiCreateCustomWorld", "GuiCustomizeWorldScreen"},
        {"Editing a world", "EditWorldScreen", "GuiRenameWorld"},
        {"In the options", "OptionsScreen", "OptionsSubScreen", "GuiOptions"},
        {"In video settings", "VideoSettingsScreen", "GuiVideoSettings"},
        {"In sound settings", "SoundOptionsScreen", "GuiScreenOptionsSounds"},
        {"In controls", "ControlsScreen", "GuiControls"},
        {"Changing keybinds", "KeyBindsScreen"},
        {"In mouse settings", "MouseSettingsScreen"},
        {"In chat settings", "ChatOptionsScreen", "ScreenChatOptions"},
        {"In accessibility settings", "AccessibilityOptionsScreen"},
        {"In online settings", "OnlineOptionsScreen"},
        {"Choosing a language", "LanguageSelectScreen", "GuiLanguage"},
        {"Choosing resource packs", "PackSelectionScreen", "GuiScreenResourcePacks"},
        {"Customizing skin layers", "SkinCustomizationScreen", "GuiCustomizeSkin"},
        {"Browsing Realms", "RealmsMainScreen", "GuiScreenRealmsProxy"},
        {"Watching the credits", "WinScreen", "GuiWinGame", "CreditsAndAttributionScreen"},
        {"Opening to LAN", "ShareToLanScreen", "GuiShareToLan"},
        {"Managing players", "SocialInteractionsScreen"},
        {"Viewing statistics", "StatsScreen", "GuiStats"},
        {"Viewing advancements", "AdvancementsScreen"},
        {"Viewing achievements", "GuiAchievements"},
        {"Paused", "PauseScreen", "GuiIngameMenu"},
        {"Chatting", "ChatScreen", "GuiChat"},
        {"Sleeping", "InBedChatScreen", "GuiSleepMP"},
        {"In the inventory", "InventoryScreen", "GuiInventory"},
        {"In the creative inventory", "CreativeModeInventoryScreen", "GuiContainerCreative"},
        {"Crafting", "CraftingScreen", "GuiCrafting", "CrafterScreen"},
        {"Smelting", "AbstractFurnaceScreen", "GuiFurnace"},
        {"Looking in a chest", "ContainerScreen", "GuiChest"},
        {"Looking in a shulker box", "ShulkerBoxScreen"},
        {"Looking in a hopper", "HopperScreen", "GuiHopper"},
        {"Looking in a dispenser", "DispenserScreen", "GuiDispenser"},
        {"Enchanting", "EnchantmentScreen", "GuiEnchantment"},
        {"Using an anvil", "AnvilScreen", "GuiRepair"},
        {"Brewing", "BrewingStandScreen", "GuiBrewingStand"},
        {"Trading", "MerchantScreen", "GuiMerchant"},
        {"Using a beacon", "BeaconScreen", "GuiBeacon"},
        {"Smithing", "SmithingScreen"},
        {"Using a grindstone", "GrindstoneScreen"},
        {"Using a stonecutter", "StonecutterScreen"},
        {"Using a loom", "LoomScreen"},
        {"Using a cartography table", "CartographyTableScreen"},
        {"Checking a mount", "HorseInventoryScreen", "GuiScreenHorseInventory", "AbstractMountInventoryScreen"},
        {"In a container", "AbstractContainerScreen", "GuiContainer"},
        {"Reading a book", "BookViewScreen", "GuiScreenBook", "LecternScreen"},
        {"Writing a book", "BookEditScreen", "BookSignScreen"},
        {"Editing a sign", "AbstractSignEditScreen", "GuiEditSign"},
        {"Editing a command block", "AbstractCommandBlockEditScreen", "GuiCommandBlock"},
        {"Died", "DeathScreen", "GuiGameOver"},
        {"Connecting to a server", "ConnectScreen", "GuiConnecting"},
        {"Loading a world", "LevelLoadingScreen", "ReceivingLevelScreen", "GuiDownloadTerrain"},
        {"Loading", "GenericMessageScreen", "ProgressScreen", "GuiScreenWorking", "GenericWaitingScreen"},
        {"Disconnected", "DisconnectedScreen", "GuiDisconnected"},
        {"In the menus", "ConfirmScreen", "GuiYesNo", "AlertScreen", "ConfirmLinkScreen", "GuiConfirmOpenLink", "PopupScreen"},
        {"Browsing mods", "ModsScreen", "GuiModList"},
        {"Configuring a mod", "GuiConfig"},
        {"In the Lads menu", "LadsSettingsScreen"},
        {"Editing the HUD", "DraggableHudScreen"},
        {"Switching accounts", "AccountSwitcherScreen"},
        {"Browsing screenshots", "ScreenshotsScreen", "ManageScreenshotsScreen", "EnlargedScreenshotScreen"},
        {"Drawing a crosshair", "CrosshairDrawingScreen"},
        {"Changing skin", "SkinChangerScreen"},
        {"In reconnect settings", "ReconnectOptionsScreen", "ReconnectActionsScreen"},
        {"In the main menu", "TitleExtrasScreen"},
    };
    private static final Map<String, String> LABELS = new HashMap<>();
    static { for (String[] row : SCREENS) for (int i = 1; i < row.length; i++) LABELS.put(row[i], row[0]); }
    private static final ClassValue<String> CACHE = new ClassValue<String>() {
        @Override protected String computeValue(Class<?> type) { return label(type); }
    };

    /** What Discord shows for a screen of this class. */
    public static String screen(Class<?> type) { return CACHE.get(type); }

    static String label(Class<?> type) {
        String named = null;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getSimpleName().replaceFirst("\\d+$", "");
            String label = LABELS.get(name);
            if (label != null) return label;
            if (named == null && !name.isEmpty()) named = name;
        }
        // An unlisted screen (another mod's): its class name, "SodiumOptionsScreen" -> "In Sodium Options".
        String name = named == null ? "" : named.replaceFirst("^Gui(?=[A-Z])", "").replaceFirst("(Screen|Gui|GUI|Menu)$", "");
        if (name.length() < 3 || name.startsWith("class_") || !name.matches("[A-Za-z0-9]+")) return "In the menus";
        return "In " + name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    /** Discord's activity text fields are 128 bytes. */
    static String text(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int codePoint : value.codePoints().toArray()) {
            if (Character.isISOControl(codePoint)) continue;
            String character = new String(Character.toChars(codePoint));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 127) break;
            result.append(character); bytes += size;
        }
        return result.toString();
    }
}
