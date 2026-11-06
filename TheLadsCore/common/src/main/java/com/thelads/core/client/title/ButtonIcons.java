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
        Map.entry("deathScreen.spectate", "eye"), Map.entry("modmenu.title", "mods"), Map.entry("fml.menu.mods", "mods"),
        // 26.x's pause and options menus, world selection and creation, the report and friends screens
        Map.entry("options.stream", "megaphone"), Map.entry("options.snooper.view", "stats"), Map.entry("menu.custom_options", "settings"), Map.entry("menu.quick_actions", "bolt"), Map.entry("menu.feedback", "chat"),
        Map.entry("options.realmsNotifications.button", "bell"), Map.entry("gui.report_to_server", "flag"),
        Map.entry("selectWorld.createDemo", "play"), Map.entry("selectWorld.upgrade_and_play", "play"), Map.entry("selectWorld.versionJoinButton", "play"),
        Map.entry("selectWorld.backupJoinConfirmButton", "save"), Map.entry("selectWorld.backupJoinSkipButton", "arrow-right"),
        Map.entry("selectWorld.backupEraseCache", "delete"), Map.entry("selectWorld.edit.resetIcon", "reset"),
        Map.entry("selectWorld.edit.export_worldgen_settings", "upload"),
        Map.entry("createWorld.tab.game.title", "controls"), Map.entry("createWorld.tab.world.title", "globe"), Map.entry("createWorld.tab.more.title", "more"),
        Map.entry("createWorld.customize.presets", "layers"), Map.entry("createWorld.customize.presets.select", "check"),
        Map.entry("createWorld.customize.flat.removeLayer", "delete"), Map.entry("createWorld.customize.custom.presets", "layers"),
        Map.entry("createWorld.customize.custom.randomize", "dice"), Map.entry("createWorld.customize.custom.defaults", "reset"),
        Map.entry("createWorld.customize.custom.prev", "arrow-left"), Map.entry("createWorld.customize.custom.next", "arrow-right"),
        Map.entry("dataPack.validation.back", "arrow-left"), Map.entry("dataPack.validation.reset", "reset"),
        Map.entry("gui.friends.accept", "check"), Map.entry("gui.friends.decline", "cross"), Map.entry("gui.friends.join", "enter"),
        Map.entry("gui.friends.cancel_request", "cross"), Map.entry("gui.friends.send_request", "user-add"), Map.entry("gui.friends.unfriend", "delete"),
        Map.entry("gui.friends.copy_to_clipboard", "copy"), Map.entry("gui.friends.open", "friends"),
        Map.entry("gui.friends.multiplayer.invite", "user-add"), Map.entry("gui.friends.multiplayer.invite_accept", "check"),
        Map.entry("gui.friends.multiplayer.invite_reject", "cross"), Map.entry("gui.friends.multiplayer.join_request", "enter"),
        Map.entry("gui.friends.multiplayer.join_request_accept", "check"), Map.entry("gui.friends.multiplayer.join_request_reject", "cross"),
        Map.entry("gui.socialInteractions.report", "flag"), Map.entry("gui.socialInteractions.hide", "eye-off"), Map.entry("gui.socialInteractions.show", "eye"),
        Map.entry("gui.abuseReport.send", "send"), Map.entry("gui.chatReport.send", "send"), Map.entry("gui.abuseReport.read_info", "info"),
        Map.entry("gui.chatReport.read_info", "info"), Map.entry("gui.abuseReport.discard.discard", "delete"), Map.entry("gui.chatReport.discard.discard", "delete"),
        Map.entry("gui.abuseReport.discard.draft", "save"), Map.entry("gui.chatReport.discard.draft", "save"),
        Map.entry("gui.abuseReport.discard.return", "edit"), Map.entry("gui.chatReport.discard.return", "edit"),
        Map.entry("mco.selectServer.play", "play"), Map.entry("mco.selectServer.configure", "settings"), Map.entry("mco.selectServer.leave", "exit"),
        Map.entry("mco.selectServer.purchase", "add"), Map.entry("mco.selectServer.create", "add"),
        Map.entry("mco.configure.world.buttons.invite", "user-add"), Map.entry("mco.configure.world.buttons.players", "users"),
        Map.entry("mco.configure.world.buttons.settings", "settings"), Map.entry("mco.configure.world.buttons.options", "settings"),
        Map.entry("mco.configure.world.buttons.resetworld", "reset"), Map.entry("mco.configure.world.buttons.delete", "delete"),
        Map.entry("mco.backup.button.download", "download"), Map.entry("mco.backup.button.upload", "upload"), Map.entry("mco.backup.button.restore", "refresh"),
        Map.entry("mco.brokenworld.download", "download"), Map.entry("mco.brokenworld.play", "play"), Map.entry("mco.brokenworld.reset", "reset"),
        Map.entry("mco.invites.button.accept", "check"), Map.entry("mco.invites.button.reject", "cross"),
        Map.entry("mco.terms.buttons.agree", "check"), Map.entry("mco.terms.buttons.disagree", "cross"), Map.entry("mco.upload.button.name", "upload"),
        Map.entry("resourcePack.openFolder", "folder-open"), Map.entry("selectWorld.rename", "edit"), Map.entry("selectWorld.renameButton", "edit"),
        Map.entry("multiplayer.connect", "enter"), Map.entry("multiplayer.stopSleeping", "exit"), Map.entry("lanServer.otherPlayers", "users"),
        Map.entry("options.multiplayer.title", "multiplayer"), Map.entry("options.qualityButton", "video"),
        Map.entry("options.performanceButton", "speed"), Map.entry("options.advancedButton", "sliders"), Map.entry("options.postButton", "sparkles"),
        Map.entry("options.showAll", "eye"), Map.entry("options.showConflicts", "warning"), Map.entry("options.showNone", "eye"),
        Map.entry("options.toggleFree", "keyboard"), Map.entry("options.sort", "filter"), Map.entry("options.confirmReset", "warning"),
        Map.entry("options.availableKeys", "keyboard"), Map.entry("editGamerule.inGame.button", "list-check"),
        Map.entry("restrictions_screen.button", "shield"), Map.entry("menu.multiplayerOptions.applyChanges", "check"),
        Map.entry("menu.multiplayerOptions.lan", "host"), Map.entry("menu.multiplayerOptions.publish.stopped", "stop"),
        Map.entry("options.graphics.warning.accept", "arrow-right"), Map.entry("options.graphics.warning.cancel", "arrow-left"),
        Map.entry("options.friendsList.confirm.turnOn", "check"), Map.entry("options.friendsList.confirm.turnOff", "cross"));

    /**
     * Option buttons ("Graphics: Fancy", "FOV: 70", a toggle's "Auto-Jump: ON") by their caption's translation key: vanilla builds the
     * message as one of the {@link #wraps} keys around the caption, so the version hooks look the caption up here. Also the captions
     * 1.8.9 shows before the colon.
     */
    private static final Map<String, String> CAPTIONS = Map.ofEntries(
        Map.entry("options.graphics", "picture"), Map.entry("options.graphics.preset", "sliders"), Map.entry("options.graphicsApi", "computer"),
        Map.entry("options.fov", "eye"), Map.entry("options.fovEffectScale", "eye"), Map.entry("options.vignette", "eye"),
        Map.entry("options.screenEffectScale", "sparkles"), Map.entry("options.damageTiltStrength", "heart"), Map.entry("options.darknessEffectScale", "moon"),
        Map.entry("options.renderDistance", "mountain"), Map.entry("options.simulationDistance", "cubes"), Map.entry("options.entityDistanceScaling", "resize"),
        Map.entry("options.framerateLimit", "speed"), Map.entry("options.framerate", "speed"), Map.entry("options.inactivityFpsLimit", "speed"),
        Map.entry("options.vsync", "refresh"), Map.entry("options.viewBobbing", "walking"), Map.entry("options.guiScale", "layout"),
        Map.entry("options.gamma", "brightness"), Map.entry("options.renderClouds", "cloud"), Map.entry("options.clouds", "cloud"),
        Map.entry("options.renderCloudsDistance", "cloud"), Map.entry("options.weatherRadius", "cloud"),
        Map.entry("options.fullscreen", "fullscreen"), Map.entry("options.exclusiveFullscreen", "fullscreen"), Map.entry("options.fullscreen.resolution", "computer"),
        Map.entry("options.particles", "sparkles"), Map.entry("options.glintSpeed", "sparkles"), Map.entry("options.glintStrength", "sparkles"),
        Map.entry("options.mipmapLevels", "layers"), Map.entry("options.maxAnisotropy", "grid"), Map.entry("options.textureFiltering", "grid"),
        Map.entry("options.entityShadows", "moon"), Map.entry("options.biomeBlendRadius", "colors"), Map.entry("options.ao", "sun"),
        Map.entry("options.attackIndicator", "target"), Map.entry("options.autosaveIndicator", "save"), Map.entry("options.prioritizeChunkUpdates", "cubes"),
        Map.entry("options.chunkFade", "clock"), Map.entry("options.cutoutLeaves", "leaf"), Map.entry("options.improvedTransparency", "picture"),
        Map.entry("options.sensitivity", "mouse"), Map.entry("options.rawMouseInput", "mouse"), Map.entry("options.allowCursorChanges", "mouse"),
        Map.entry("options.ctrlClickEmulatesRightClick", "mouse"), Map.entry("options.invertMouse", "exchange"), Map.entry("options.invertMouseX", "exchange"),
        Map.entry("options.invertMouseY", "exchange"), Map.entry("options.discrete_mouse_scroll", "scroll"), Map.entry("options.mouseWheelSensitivity", "scroll"),
        Map.entry("options.touchscreen", "touch"), Map.entry("options.autoJump", "arrow-up"), Map.entry("key.sneak", "arrow-small-down"),
        Map.entry("key.sprint", "running"), Map.entry("options.sprintWindow", "running"), Map.entry("options.quitShortcuts", "power"),
        Map.entry("options.operatorItemsTab", "box"), Map.entry("options.difficulty", "shield"), Map.entry("options.difficulty.online", "shield"),
        Map.entry("options.showSubtitles", "subtitles"), Map.entry("options.directionalAudio", "headphones"), Map.entry("options.audioDevice", "speaker"),
        Map.entry("options.music_frequency", "music"), Map.entry("options.musicToast", "music"), Map.entry("options.showNowPlayingToast", "music"),
        Map.entry("options.narrator", "megaphone"), Map.entry("options.accessibility.narrator_hotkey", "keyboard"),
        Map.entry("options.accessibility.high_contrast", "brightness"), Map.entry("options.accessibility.high_contrast_block_outline", "cube"),
        Map.entry("options.accessibility.menu_background_blurriness", "eye"), Map.entry("options.accessibility.panorama_speed", "speed"),
        Map.entry("options.accessibility.text_background", "text-box"), Map.entry("options.accessibility.text_background_opacity", "text-box"),
        Map.entry("options.accessibility.link", "link"), Map.entry("options.hideLightningFlashes", "bolt"), Map.entry("options.darkMojangStudiosBackgroundColor", "moon"),
        Map.entry("options.hideSplashTexts", "text"), Map.entry("options.font", "text"), Map.entry("options.forceUnicodeFont", "text"),
        Map.entry("options.japaneseGlyphVariants", "language"), Map.entry("options.chat.visibility", "chat"), Map.entry("options.chat.color", "colors"),
        Map.entry("options.chat.links", "link"), Map.entry("options.chat.links.prompt", "link"), Map.entry("options.chat.opacity", "eye"),
        Map.entry("options.chat.scale", "text-size"), Map.entry("options.chat.line_spacing", "text-align"), Map.entry("options.chat.delay", "clock"),
        Map.entry("options.chat.width", "resize"), Map.entry("options.chat.height.focused", "resize"), Map.entry("options.chat.height.unfocused", "resize"),
        Map.entry("options.chat.drafts", "save"), Map.entry("options.autoSuggestCommands", "terminal"), Map.entry("options.hideMatchedNames", "eye-off"),
        Map.entry("options.reducedDebugInfo", "bug"), Map.entry("options.onlyShowSecureChat", "shield"), Map.entry("options.inGameNotification", "bell"),
        Map.entry("options.modelPart.cape", "shirt"), Map.entry("options.modelPart.hat", "shirt"), Map.entry("options.modelPart.jacket", "shirt"),
        Map.entry("options.modelPart.left_sleeve", "shirt"), Map.entry("options.modelPart.right_sleeve", "shirt"),
        Map.entry("options.modelPart.left_pants_leg", "shirt"), Map.entry("options.modelPart.right_pants_leg", "shirt"),
        Map.entry("options.showCape", "shirt"), Map.entry("options.mainHand", "hand"),
        Map.entry("options.realmsNotifications", "bell"), Map.entry("options.allowServerListing", "server"), Map.entry("options.allowFriendRequests", "friends"),
        Map.entry("options.friendsList", "friends"), Map.entry("options.sharePresence", "eye"), Map.entry("options.notifications.display_time", "clock"),
        Map.entry("options.rotateWithMinecart", "refresh"), Map.entry("options.online.xboxSettings", "user"),
        Map.entry("options.worldOptions.game_mode", "dice"), Map.entry("options.worldOptions.guest.command_access", "terminal"),
        Map.entry("options.worldOptions.personal_game_mode", "dice"), Map.entry("options.snooper", "stats"), Map.entry("options.anaglyph", "eye"),
        Map.entry("options.fboEnable", "layers"), Map.entry("options.useVbo", "bolt"), Map.entry("options.vbo", "bolt"), Map.entry("options.blockAlternatives", "cube"),
        Map.entry("soundCategory.master", "volume"), Map.entry("soundCategory.music", "music"), Map.entry("soundCategory.record", "disk"),
        Map.entry("soundCategory.weather", "cloud"), Map.entry("soundCategory.block", "cube"), Map.entry("soundCategory.hostile", "warning"),
        Map.entry("soundCategory.neutral", "heart"), Map.entry("soundCategory.player", "user"), Map.entry("soundCategory.ambient", "leaf"),
        Map.entry("soundCategory.voice", "microphone"), Map.entry("soundCategory.ui", "mouse"),
        Map.entry("selectWorld.gameMode", "dice"), Map.entry("selectWorld.moreWorldOptions", "dots"), Map.entry("selectWorld.allowCommands", "terminal"),
        Map.entry("selectWorld.allowCommands.new", "terminal"), Map.entry("selectWorld.bonusItems", "box"), Map.entry("selectWorld.mapFeatures", "home"),
        Map.entry("selectWorld.mapType", "world"), Map.entry("selectWorld.import_worldgen_settings", "download"), Map.entry("selectWorld.customizeType", "settings"),
        Map.entry("selectWorld.gameRules", "list-check"), Map.entry("selectWorld.dataPacks", "box-open"), Map.entry("selectWorld.experiments", "bolt"),
        Map.entry("addServer.resourcePack", "layers"), Map.entry("addServer.hideAddress", "eye-off"),
        Map.entry("key.attack", "target"), Map.entry("key.use", "hand"),
        Map.entry("options.chat.delay_none", "clock"), Map.entry("options.chat.delay_instant", "clock"),
        Map.entry("options.chunkFade.none", "clock"), Map.entry("options.chunkFade.seconds", "clock"),
        Map.entry("options.fullscreen.exclusive.mode", "computer"), Map.entry("options.macFullscreenMenuVisibility", "layout"),
        Map.entry("options.worldOptions.guest.force_game_mode", "dice"), Map.entry("options.saturation", "colors"),
        Map.entry("options.advancedOpengl", "computer"), Map.entry("options.postProcessEnable", "sparkles"),
        Map.entry("options.music", "music"), Map.entry("options.sound", "volume"));

    /** The message keys vanilla builds an option button's text with, around its caption ("%s: %s", "%s: ON", "%s: %s%%" ...). */
    private static final java.util.Set<String> WRAPPERS = java.util.Set.of("options.generic_value", "options.on.composed", "options.off.composed",
        "options.percent_value", "options.percent_add_value", "options.pixel_value");

    /** Whether a message with this translation key is an option button's text, its first argument the caption (look that up instead). */
    public static boolean wraps(String key) {
        return WRAPPERS.contains(key);
    }

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
        Map.entry("Mute", "mute"), Map.entry("Unban", "unlock"), Map.entry("Add Offline Account", "user-add"),
        // Mods' and other plain-text buttons
        Map.entry("Options", "settings"), Map.entry("Config", "settings"), Map.entry("Configure", "settings"), Map.entry("Edit", "edit"),
        Map.entry("Create", "add"), Map.entry("New", "add"), Map.entry("Open Folder", "folder-open"), Map.entry("Open folder", "folder-open"),
        Map.entry("Open Mods Folder", "folder-open"), Map.entry("Reload", "refresh"), Map.entry("Retry", "refresh"), Map.entry("Continue", "arrow-right"),
        Map.entry("Reset to Default", "reset"), Map.entry("Reset All", "reset"), Map.entry("Defaults", "reset"), Map.entry("Help", "help"), Map.entry("Info", "info"),
        Map.entry("Super Secret Settings...", "sparkles"), Map.entry("Show Conflicts", "warning"), Map.entry("Show Unbound", "eye"),
        Map.entry("Show All", "eye"), Map.entry("Confirm?", "warning"),
        Map.entry("OptiFine Settings...", "settings"), Map.entry("Kill Banners", "trophy"), Map.entry("Combo Sounds", "music"),
        Map.entry("Export", "upload"), Map.entry("Upload", "upload"), Map.entry("Download", "download"), Map.entry("Exit", "exit"), Map.entry("Select", "check"));

    /**
     * The icon of a button with this message: its translation key (null for plain text) and its plain text (null when the message is
     * not just one string); the key wins. Allocation-free, safe to ask every frame.
     */
    public static String of(String key, String text) {
        String icon = key == null ? null : KEYS.get(key);
        if (icon == null && key != null) icon = CAPTIONS.get(key);
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
            for (Map<String, String> table : java.util.List.of(KEYS, CAPTIONS))
                for (Map.Entry<String, String> e : table.entrySet()) {
                    String label = translate.apply(e.getKey());
                    if (label != null && !label.equals(e.getKey())) {
                        built.putIfAbsent(label, e.getValue());
                        if (label.endsWith(":")) built.putIfAbsent(label.substring(0, label.length() - 1).trim(), e.getValue());
                    }
                }
            translated = built;
            translatedLanguage = language;
        }
        return translated.get(text);
    }

    /**
     * 1.8.9's option buttons draw "Caption: value" as one string: the icon of the text, else of the text before its first colon.
     * Allocates that caption, so ask once per text, not per frame.
     */
    public static String ofCaptioned(String text, String language, java.util.function.Function<String, String> translate) {
        String icon = ofText(text, language, translate);
        int colon = icon != null || text == null ? -1 : text.indexOf(':');
        return colon > 0 ? ofText(text.substring(0, colon).trim(), language, translate) : icon; // .trim(): French writes "Caption : value"
    }

    private static Map<String, String> translated;
    private static String translatedLanguage;

    /** All icon names the maps use, for the check that each has a glyph. */
    static java.util.stream.Stream<String> icons() {
        return java.util.stream.Stream.of(KEYS, CAPTIONS, LITERALS).flatMap(m -> m.values().stream());
    }
}
