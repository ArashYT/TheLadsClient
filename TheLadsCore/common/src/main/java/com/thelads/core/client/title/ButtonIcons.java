package com.thelads.core.client.title;

import java.util.Map;

/**
 * Which icon (a TitleScreenTheme.icon kind, a name of tools/icons/icons.json) a button draws before its label: vanilla's buttons by
 * their translation key (the same on every version, whatever the language), the Lads' own and a mod's plain-text ones by their English
 * label. A label with no sensible icon maps to null and its button stays as it is: no filler glyph. Visual only.
 */
public final class ButtonIcons {
    private ButtonIcons() {}

    private static final Map<String, String> KEYS = Map.ofEntries(
        Map.entry("gui.done", "check"), Map.entry("gui.cancel", "cross"), Map.entry("gui.yes", "check"), Map.entry("gui.no", "cross"),
        Map.entry("gui.ok", "check"), Map.entry("gui.acknowledge", "check"), Map.entry("gui.proceed", "arrow-right"), Map.entry("gui.continue", "arrow-right"),
        Map.entry("gui.back", "arrow-left"), Map.entry("gui.toMenu", "arrow-left"), Map.entry("gui.toTitle", "arrow-left"),
        Map.entry("gui.toWorld", "arrow-left"), Map.entry("gui.toRealms", "arrow-left"),
        Map.entry("gui.advancements", "advancements"), Map.entry("gui.achievements", "advancements"), Map.entry("gui.stats", "stats"),
        Map.entry("gui.copy_link_to_clipboard", "copy"), Map.entry("gui.open_report_dir", "folder-open"), Map.entry("gui.remove", "delete"),
        Map.entry("menu.returnToGame", "play"), Map.entry("menu.returnToMenu", "exit"), Map.entry("menu.disconnect", "exit"),
        Map.entry("menu.quit", "power"), Map.entry("menu.options", "settings"), Map.entry("menu.singleplayer", "user"),
        Map.entry("menu.multiplayer", "multiplayer"), Map.entry("menu.online", "realms"), Map.entry("menu.shareToLan", "host"),
        Map.entry("menu.multiplayerOptions.button", "host"), Map.entry("lanServer.start", "play"),
        Map.entry("menu.playdemo", "play"), Map.entry("menu.resetdemo", "refresh"), Map.entry("menu.sendFeedback", "chat"),
        Map.entry("menu.reportBugs", "bug"), Map.entry("menu.server_links", "link"), Map.entry("menu.playerReporting", "flag"),
        Map.entry("selectServer.select", "enter"), Map.entry("selectServer.add", "add"), Map.entry("selectServer.edit", "edit"),
        Map.entry("selectServer.delete", "delete"), Map.entry("selectServer.deleteButton", "delete"), Map.entry("selectServer.refresh", "refresh"),
        Map.entry("selectServer.direct", "link"), Map.entry("addServer.add", "check"),
        Map.entry("selectWorld.select", "play"), Map.entry("selectWorld.create", "add"), Map.entry("selectWorld.edit", "edit"),
        Map.entry("selectWorld.delete", "delete"), Map.entry("selectWorld.deleteButton", "delete"), Map.entry("selectWorld.recreate", "refresh"),
        Map.entry("selectWorld.edit.save", "save"), Map.entry("selectWorld.edit.backup", "save"), Map.entry("selectWorld.edit.openFolder", "folder-open"),
        Map.entry("selectWorld.edit.backupFolder", "folder-open"), Map.entry("selectWorld.edit.optimize", "bolt"),
        Map.entry("options.video", "video"), Map.entry("options.controls", "controls"), Map.entry("options.language", "language"),
        Map.entry("options.sounds", "volume"), Map.entry("options.resourcepack", "layers"), Map.entry("options.skinCustomisation", "wardrobe"),
        Map.entry("options.accessibility", "access"), Map.entry("options.accessibility.title", "access"), Map.entry("options.chat", "chat"), Map.entry("options.chat.title", "chat"),
        Map.entry("options.online", "realms"), Map.entry("options.mouse_settings", "mouse"), Map.entry("controls.keybinds", "keyboard"),
        Map.entry("options.telemetry", "stats"), Map.entry("options.telemetry.button", "stats"), Map.entry("options.credits_and_attribution", "info"),
        Map.entry("options.worldOptions.button", "settings"), Map.entry("controls.reset", "reset"), Map.entry("controls.resetAll", "reset"),
        Map.entry("pack.openFolder", "folder-open"), Map.entry("deathScreen.respawn", "refresh"), Map.entry("deathScreen.titleScreen", "exit"),
        Map.entry("deathScreen.spectate", "eye"), Map.entry("modmenu.title", "mods"), Map.entry("fml.menu.mods", "mods"));

    private static final Map<String, String> LITERALS = Map.ofEntries(
        Map.entry("Done", "check"), Map.entry("Cancel", "cross"), Map.entry("Back", "arrow-left"), Map.entry("Apply", "check"), Map.entry("Confirm", "check"),
        Map.entry("Yes", "check"), Map.entry("No", "cross"), Map.entry("OK", "check"), Map.entry("Save", "save"), Map.entry("Save drawing", "save"),
        Map.entry("Save Selected Packs", "save"), Map.entry("Add", "add"), Map.entry("Add folder", "add"), Map.entry("Add Folder", "add"),
        Map.entry("Remove", "delete"), Map.entry("Delete", "delete"), Map.entry("Clear", "delete"), Map.entry("Undo", "reset"),
        Map.entry("Join", "enter"), Map.entry("Reconnect", "refresh"), Map.entry("Relaunch Now", "refresh"), Map.entry("Cancel reconnect", "cross"),
        Map.entry("Previous", "arrow-left"), Map.entry("Next", "arrow-right"), Map.entry("Copy crash report", "copy"),
        Map.entry("Quit Game", "power"), Map.entry("Return to title", "exit"), Map.entry("Switch Versions", "switch"),
        Map.entry("Lads Mods", "lads"), Map.entry("Lads Client", "lads"), Map.entry("More...", "dots"), Map.entry("Extras...", "dots"),
        Map.entry("Replays", "replay"), Map.entry("Screenshots", "camera"), Map.entry("Cosmetics", "wardrobe"), Map.entry("Friends", "friends"),
        Map.entry("Lads friends", "friends"), Map.entry("Mojang friends", "friends"), Map.entry("Manage Mojang friends", "friends"),
        Map.entry("Requests", "mail"), Map.entry("Blocked", "block"), Map.entry("Enabled", "check"), Map.entry("Add on Lads", "user-add"), Map.entry("Mods", "mods"), Map.entry("Accounts", "user"),
        Map.entry("Profiles", "user"), Map.entry("Discover Servers", "globe"), Map.entry("Tracker layout", "layout"), Map.entry("Toast layout", "layout"),
        // The Lads' own screens (drawn with LadsGraphics: settings, dialogs, friends, host)
        Map.entry("Colors", "colors"), Map.entry("Edit HUD", "edit"), Map.entry("Resource packs", "layers"),
        Map.entry("Packs", "layers"), Map.entry("Video settings", "video"), Map.entry("Video", "video"), Map.entry("Installed mods", "mods"),
        Map.entry("Reset options", "reset"), Map.entry("Reset", "reset"), Map.entry("Favorites", "favorite"), Map.entry("Import", "download"),
        Map.entry("Import from client", "download"), Map.entry("Import Client...", "download"), Map.entry("Import code", "download"),
        Map.entry("The Lads Network", "server"), Map.entry("Lads Network", "network"), Map.entry("Network", "network"), Map.entry("Modules", "arrow-left"), Map.entry("Undo bypass", "reset"), Map.entry("Bypass...", "warning"),
        Map.entry("Close", "cross"), Map.entry("Cancel (safe)", "cross"), Map.entry("Upload new...", "upload"), Map.entry("Play", "play"),
        Map.entry("Play selection", "play"), Map.entry("Use whole file", "check"), Map.entry("Load", "download"), Map.entry("Save as...", "save"),
        Map.entry("Rename", "edit"), Map.entry("Copy share code", "copy"), Map.entry("Clear slot", "delete"), Map.entry("Reset wheel", "reset"),
        Map.entry("Key Binds", "keyboard"), Map.entry("Refresh", "refresh"), Map.entry("Settings", "settings"),
        Map.entry("Chat", "chat"), Map.entry("Invite", "user-add"), Map.entry("Accept", "check"), Map.entry("Decline", "cross"),
        Map.entry("Unblock", "unlock"), Map.entry("Send Request", "user-add"), Map.entry("Add Friend", "user-add"), Map.entry("Send", "send"),
        Map.entry("Set Nickname", "edit"), Map.entry("Block", "block"), Map.entry("Kick", "exit"), Map.entry("Ban", "block"),
        Map.entry("Mute", "mute"), Map.entry("Unban", "unlock"), Map.entry("Add Offline Account", "user-add"));

    /**
     * The icon of a button with this message: its translation key (null for plain text) and its plain text (null when the message is
     * not just one string); the key wins. Allocation-free, safe to ask every frame.
     */
    public static String of(String key, String text) {
        String icon = key == null ? null : KEYS.get(key);
        return icon != null || text == null ? icon : LITERALS.get(text);
    }

    /**
     * The icon of a button that only has its drawn text (1.8.9): its English label, or vanilla's label translated into the game's
     * language ({@code translate} turns a translation key into the text shown), the table rebuilt when the language changes.
     */
    public static String ofText(String text, String language, java.util.function.Function<String, String> translate) {
        String icon = text == null ? null : LITERALS.get(text);
        if (icon != null || text == null || text.isEmpty()) return icon;
        if (translated == null || !language.equals(translatedLanguage)) {
            Map<String, String> built = new java.util.HashMap<>();
            for (Map.Entry<String, String> e : KEYS.entrySet()) {
                String label = translate.apply(e.getKey());
                if (label != null && !label.equals(e.getKey())) built.putIfAbsent(label, e.getValue());
            }
            translated = built;
            translatedLanguage = language;
        }
        return translated.get(text);
    }

    private static Map<String, String> translated;
    private static String translatedLanguage;

    /** All icon names the maps use, for the check that each has a glyph. */
    static java.util.stream.Stream<String> icons() {
        return java.util.stream.Stream.concat(KEYS.values().stream(), LITERALS.values().stream());
    }
}
