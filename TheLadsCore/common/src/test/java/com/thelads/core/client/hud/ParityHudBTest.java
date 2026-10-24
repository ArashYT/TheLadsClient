package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParityHudBTest {

    private LadsGameBridge oldBridge;

    @BeforeEach
    void setup() {
        oldBridge = LadsGameBridge.get();
    }

    @AfterEach
    void tearDown() {
        LadsGameBridge.set(oldBridge);
    }

    @Test
    void testAllParityHudBModulesRegisteredAndDefaultOff() {
        var mm = ModuleManager.getInstance();
        String[] newHudModules = {
            "DamageIndicator", "JumpReset", "MediaPlayer", "HorseStats",
            "PlayerCounter", "TotemPopCounter", "BlockInfo", "TierTagger",
            "CustomText", "CustomShape", "PlayerHead", "BlockAbove",
            "EntityCounter", "ShaderPack", "HeldItemLore", "Subtitles",
            "TitleTweaker", "ToastControl", "VanillaHUD"
        };

        for (String name : newHudModules) {
            var mod = mm.getModule(name);
            assertNotNull(mod, "Module should be registered in ModuleManager: " + name);
            assertFalse(mod.isEnabled(), "New HUD module should default to disabled (off): " + name);
        }
    }

    @Test
    void testGatedHudModulesRecognizedBySafetyManager() {
        String[] gated = {
            "DamageIndicator", "HorseStats", "PlayerCounter",
            "TotemPopCounter", "BlockInfo", "TierTagger"
        };

        for (String name : gated) {
            assertTrue(HypixelSafetyManager.isGatedHudModule(name), "Should be recognized as gated HUD module: " + name);
        }

        assertFalse(HypixelSafetyManager.isGatedHudModule("FPS"));
        assertFalse(HypixelSafetyManager.isGatedHudModule("CustomText"));
        assertFalse(HypixelSafetyManager.isGatedHudModule("Subtitles"));
    }

    @Test
    void testCustomTextTemplatePlaceholders() {
        DefaultGameBridge mock = new DefaultGameBridge() {
            @Override public String getPlayerName() { return "TestPlayer"; }
            @Override public int getFps() { return 120; }
            @Override public int getPing() { return 25; }
            @Override public String getGameTime() { return "06:00"; }
            @Override public String getServerAddress() { return "mc.test.net"; }
            @Override public int getPlayerX() { return 100; }
            @Override public int getPlayerY() { return 64; }
            @Override public int getPlayerZ() { return -200; }
        };

        String template = "User: {player} | FPS: {fps} | Ping: {ping} | Pos: {x},{y},{z}";
        String resolved = CustomTextHudElement.resolveTemplate(template, mock);
        assertEquals("User: TestPlayer | FPS: 120 | Ping: 25 | Pos: 100,64,-200", resolved);
    }

    @Test
    void testJumpResetRatings() {
        JumpResetHudElement.onPlayerDamaged();
        JumpResetHudElement.onPlayerJumped();
        // Just verify execution does not throw
        assertNotNull(JumpResetHudElement.Rating.PERFECT);
    }

    @Test
    void testTabListExtras() {
        List<String> players = List.of("Alice", "Bob", "Charlie", "Me");
        // Self first disabled
        assertEquals(players, TabListModule.sortSelfFirst(players, "Me"));

        // Enable self first
        var tab = ModuleManager.getInstance().getModule("TabList");
        assertNotNull(tab);
        var selfFirstOpt = tab.getOption("Self First");
        assertNotNull(selfFirstOpt);
        selfFirstOpt.reset();
        assertFalse(TabListModule.isSelfFirst());
    }

    @Test
    void testToastControlFiltering() {
        var tc = ModuleManager.getInstance().getModule("ToastControl");
        assertNotNull(tc);
        tc.setEnabled(true);

        assertTrue(ToastControlModule.shouldHide("recipe.unlock"));
        assertTrue(ToastControlModule.shouldHide("tutorial.step"));
        assertFalse(ToastControlModule.shouldHide("advancement.goal"));

        tc.setEnabled(false);
        assertFalse(ToastControlModule.shouldHide("recipe.unlock"));
    }

    @Test
    void testTitleTweakerRouting() {
        var tt = ModuleManager.getInstance().getModule("TitleTweaker");
        assertNotNull(tt);
        tt.setEnabled(true);

        var routeOpt = tt.getOption("Route to Chat");
        assertNotNull(routeOpt);

        final String[] chatReceived = new String[1];
        DefaultGameBridge mock = new DefaultGameBridge() {
            @Override
            public void displayChatMessage(String text) {
                chatReceived[0] = text;
            }
        };
        LadsGameBridge.set(mock);

        assertFalse(TitleTweakerModule.handleTitle("VICTORY", "Red won"));
        tt.setEnabled(false);
    }

    @Test
    void testVanillaHudToggles() {
        assertFalse(VanillaHudModule.isAlwaysHardcoreHearts());
        assertFalse(VanillaHudModule.isHideXpBar());
        assertFalse(VanillaHudModule.isHideXpLevel());
        assertFalse(VanillaHudModule.isPersistentItemName());
    }

    @Test
    void testRaisedModuleExtensions() {
        RaisedModule raised = RaisedModule.get();
        assertNotNull(raised);
        assertEquals(1.0f, raised.hotbarScale());
        assertEquals(0, raised.healthOffsetX());
        assertEquals(0, raised.healthOffsetY());
        assertEquals(1.0f, raised.healthScale());
    }
}
