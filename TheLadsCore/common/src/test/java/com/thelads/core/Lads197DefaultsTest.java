package com.thelads.core;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.modules.TitleScreenModule;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Lads197DefaultsTest {

    private static final Set<String> HIDDEN_MODULES = Set.of(
        "Async", "BetterStats", "Cosmetics", "DisableNarrator", "EntityCulling", "Exordium", "FerriteCore",
        "Flashback Settings", "Jade", "Jasione", "JEI (Just Enough Items)", "Lithium",
        "ModernAdvancements", "NotEnoughAnimations", "Resourcify", "ScalableLux",
        "TitleScreen", "XaeroMinimap", "XaeroWorldmap"
    );

    private static final Set<String> DEFAULT_ENABLED_VISIBLE = Set.of(
        "1.7 Animations", "Action Bar", "AppleSkin", "ArmorHUD", "AutoReconnect", "Bed Wars Height Limit",
        "BetterF3", "BetterScreenshots", "BorderlessFullscreen", "BossBar", "Chat",
        "ClientTools", com.thelads.core.modules.CloudHeightModule.NAME, "Clumps", "Coordinates", "Crosshair Tweaks",
        "DiscordRPC", "DynamicFPS", com.thelads.core.modules.DynamicLightsModule.NAME, "EnhancedToolbars", "EnhancedTooltips",
        "FarBlockEntities", "FPS", "HYPIXEL Mods", "Quickplay", com.thelads.core.modules.ItemPhysicsModule.NAME, com.thelads.core.client.KeepUiOpen.MODULE_NAME, "KillBanner",
        com.thelads.core.modules.MenuBlurModule.NAME, "Minimap", "MouseTweaks", "Nametags", com.thelads.core.client.DamageTilt.MODULE, "Particle Core",
        "PingView", "PingHUD", "Potion Effects", "Raised", "RawInput",
        com.thelads.core.client.pack.PackMenuSettings.MODULE_NAME, "Scoreboard", "ShulkerBoxUtils", "SignalLoss", "SkinLayers",
        "SmoothHotbar", com.thelads.core.modules.StackRefillModule.NAME, com.thelads.core.modules.SurvivalTweaksModule.NAME, "TabList", "Threads", "Title Scale", "TNT Timer",
        "Toggle Sprint & Sneak", "VerticalBobbing", "Voice Chat", "Voice Chat Group", com.thelads.core.modules.WavyCapesModule.NAME,
        "Zoom"
    );

    @Test
    void hiddenModulesNeverShowInModulesListAndTitleScreenCannotBeDisabled() throws Exception {
        var mm = ModuleManager.getInstance();
        for (String hidden : HIDDEN_MODULES) {
            assertTrue(mm.isHiddenModule(hidden), "Expected hidden module: " + hidden);
        }

        TitleScreenModule titleScreen = (TitleScreenModule) mm.getModule("TitleScreen");
        assertNotNull(titleScreen);
        assertTrue(titleScreen.isEnabled());
        titleScreen.setEnabled(false);
        assertTrue(titleScreen.isEnabled(), "TitleScreen must always remain enabled");
        Module narrator = mm.getModule("DisableNarrator");
        narrator.setEnabled(false);
        assertTrue(narrator.isEnabled(), "1.9.10: DisableNarrator is always on");
        Module cosmetics = mm.getModule("Cosmetics");
        cosmetics.setEnabled(false);
        assertTrue(cosmetics.isEnabled(), "1.9.12: Cosmetics is always on (its settings are the Cosmetics screen)");

        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            for (Module m : mm.getModules()) {
                ModuleSupport.registerBuiltIn(m.getName());
            }
            var screen = new LadsSettingsScreen();
            Set<String> shown = screen.getFilteredModules().stream().map(Module::getName).collect(Collectors.toSet());
            for (String hidden : HIDDEN_MODULES) {
                assertFalse(shown.contains(hidden), "Hidden module should not appear in Modules list: " + hidden);
            }
        }
    }

    @Test
    void hypixelModsDeactivatesWhenNotOnHypixel() {
        Module hypixel = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        assertNotNull(hypixel);
        boolean wasEnabled = hypixel.isEnabled();
        var oldBridge = com.thelads.core.client.bridge.LadsGameBridge.get();
        try {
            hypixel.setEnabled(true);
            HypixelSafetyManager.qaOverride = false;
            com.thelads.core.client.bridge.LadsGameBridge.set(new com.thelads.core.client.bridge.DefaultGameBridge() {
                @Override public String getServerAddress() { return "play.example.com"; }
            });
            assertFalse(HypixelSafetyManager.isHypixel());
            assertFalse(HypixelMods.isActiveOnCurrentServer());

            com.thelads.core.client.bridge.LadsGameBridge.set(new com.thelads.core.client.bridge.DefaultGameBridge() {
                @Override public String getServerAddress() { return "mc.hypixel.net"; }
            });
            assertTrue(HypixelSafetyManager.isHypixel());
            assertTrue(HypixelMods.isActiveOnCurrentServer());
        } finally {
            com.thelads.core.client.bridge.LadsGameBridge.set(oldBridge);
            hypixel.setEnabled(wasEnabled);
        }
    }

    @Test
    void defaultEnabledModulesAndSpecificOptionDefaultsMatch197Spec() {
        var mm = ModuleManager.getInstance();
        for (Module m : mm.getModules()) {
            if (mm.isHiddenModule(m.getName())) continue;
            boolean expected = DEFAULT_ENABLED_VISIBLE.contains(m.getName());
            assertEquals(expected, m.isEnabled(), "Default enabled mismatch for module: " + m.getName());
        }

        Module toggleSprint = mm.getModule("Toggle Sprint & Sneak");
        assertTrue(((BoolOption) toggleSprint.getOption("Sprint toggled")).get());

        KillBannerModule killBanner = (KillBannerModule) mm.getModule("KillBanner");
        assertEquals("Reaver", killBanner.bannerStyle.getValue());
        assertEquals("Red", killBanner.reaverVariant.getValue());
        assertTrue(killBanner.mobs.get(), "KillBanner mobs must be true by default in 1.9.14");

        com.thelads.core.modules.CloudHeightModule cloudHeight = (com.thelads.core.modules.CloudHeightModule) mm.getModule(com.thelads.core.modules.CloudHeightModule.NAME);
        assertNotNull(cloudHeight);
        assertTrue(cloudHeight.isEnabled());
        assertEquals(com.thelads.core.modules.CloudHeightModule.MAX_VALUE, cloudHeight.height.getValue());
        assertEquals("MAX", cloudHeight.height.display());

        Module chat = mm.getModule("Chat");
        assertFalse(((BoolOption) chat.getOption("Chat Background")).get());
        assertTrue(((BoolOption) chat.getOption("Message Animations")).get());

        Module nametags = mm.getModule("Nametags");
        assertFalse(((BoolOption) nametags.getOption("Render Background")).get());

        Module potionEffects = mm.getModule("Potion Effects");
        assertTrue(potionEffects.isEnabled());

        Module scoreboard = mm.getModule("Scoreboard");
        assertEquals("Off", ((DropdownOption) scoreboard.getOption("Background")).getValue());
        assertTrue(((BoolOption) scoreboard.getOption("Hide Sequential Only")).get());

        Module tabList = mm.getModule("TabList");
        assertTrue(tabList.isEnabled());
        assertEquals("Off", ((DropdownOption) tabList.getOption("Background")).getValue());

        com.thelads.core.modules.SurvivalTweaksModule survivalTweaks = (com.thelads.core.modules.SurvivalTweaksModule) mm.getModule(com.thelads.core.modules.SurvivalTweaksModule.NAME);
        assertTrue(survivalTweaks.boat360View.get());
        assertTrue(survivalTweaks.longerChatHistory.get());
        assertTrue(survivalTweaks.fastIpPing.get());
        assertTrue(survivalTweaks.keepBarsWhileRiding.get());
    }

    @Test
    void defaultHudSettingsMatchZeroSmpLayout() {
        HudSettings fresh = new HudSettings();
        assertEquals(30, fresh.getPositions().size());
        assertEquals(30, fresh.getAnchors().size());
        assertNull(fresh.getPosition("Action Bar"), "1.9.10: the Action Bar follows Minecraft's place over the hotbar (HudDefaults)");
        assertEquals(2, fresh.getGroups().size());
        assertArrayEquals(new int[]{4, 84}, fresh.getPosition("FPS"));
        assertArrayEquals(new int[]{4, 120}, fresh.getPosition("Coordinates"));
        assertArrayEquals(new int[]{4, 6}, fresh.getPosition("Keystrokes"));
        assertArrayEquals(new int[]{4, 102}, fresh.getPosition("PingHUD"));
        assertEquals(Set.of("XP", "Hunger", "Health"), fresh.getGroupMembers("Health"));
        assertEquals(Set.of("PingHUD", "Keystrokes", "Coordinates", "FPS"), fresh.getGroupMembers("FPS"));
    }

    @Test
    void pre197ConfigIsOverriddenTo197PresetOnce() throws Exception {
        java.io.File temp = java.nio.file.Files.createTempFile("lads-197-override-", ".json").toFile();
        temp.deleteOnExit();
        try {
            com.thelads.core.config.ConfigManager.setTestConfigFile(temp);
            // Simulate a pre-1.9.7 config with non-default HUD position and disabled FPS/Scoreboard background On
            com.google.gson.JsonObject oldRoot = new com.google.gson.JsonObject();
            oldRoot.addProperty("enforce197PresetMigration", true);
            com.google.gson.JsonObject oldMods = new com.google.gson.JsonObject();
            com.google.gson.JsonObject oldFps = new com.google.gson.JsonObject();
            oldFps.addProperty("enabled", false);
            oldMods.add("FPS", oldFps);
            oldRoot.add("modules", oldMods);
            com.google.gson.JsonObject oldHud = new com.google.gson.JsonObject();
            com.google.gson.JsonObject oldPos = new com.google.gson.JsonObject();
            com.google.gson.JsonArray xy = new com.google.gson.JsonArray();
            xy.add(500);
            xy.add(500);
            oldPos.add("FPS", xy);
            oldHud.add("positions", oldPos);
            oldRoot.add("hud", oldHud);
            java.nio.file.Files.writeString(temp.toPath(), oldRoot.toString());

            ModuleManager.getInstance().getModule("FPS").setEnabled(false);
            HudSettings.getInstance().setPosition("FPS", 500, 500);

            com.thelads.core.config.ConfigManager.load();

            assertTrue(ModuleManager.getInstance().getModule("FPS").isEnabled(), "1.9.7 migration must restore FPS enabled=true");
            assertArrayEquals(new int[]{4, 84}, HudSettings.getInstance().getPosition("FPS"), "1.9.7 migration must restore default HUD positions");
            String migratedText = java.nio.file.Files.readString(temp.toPath());
            assertTrue(migratedText.contains("\"" + com.thelads.core.config.ConfigManager.PRESET_197_FLAG + "\": true"));
        } finally {
            com.thelads.core.config.ConfigManager.setTestConfigFile(null);
            ModuleManager.getInstance().resetToDefaults();
            HudSettings.getInstance().resetToDefaults();
        }
    }
}
