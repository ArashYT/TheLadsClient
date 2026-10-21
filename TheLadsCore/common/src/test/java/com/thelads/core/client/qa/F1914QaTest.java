package com.thelads.core.client.qa;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.SmoothScroll;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.PingHudElement;
import com.thelads.core.client.hypixel.HypixelTabFill;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CloudHeightModule;
import com.thelads.core.modules.KeystrokesModule;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class F1914QaTest {
    private List<String> screenshotsTaken;
    private List<String> screensOpened;
    private boolean armorStandSummoned;
    private boolean playerArmorEquipped;
    private boolean completed;
    private int finalPassed;
    private int finalFailed;
    private LadsGameBridge bridgeBefore;

    @BeforeEach
    void setUp() {
        screenshotsTaken = new ArrayList<>();
        screensOpened = new ArrayList<>();
        armorStandSummoned = false;
        playerArmorEquipped = false;
        completed = false;
        finalPassed = 0;
        finalFailed = 0;
        bridgeBefore = LadsGameBridge.get();

        // Ensure mock bridge is installed for mock tests
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override
            public List<ArmorPiece> getArmor() {
                return playerArmorEquipped
                        ? List.of(new ArmorPiece("Diamond Boots", 429, 429),
                                  new ArmorPiece("Diamond Leggings", 495, 495),
                                  new ArmorPiece("Diamond Chestplate", 528, 528),
                                  new ArmorPiece("Diamond Helmet", 363, 363))
                        : Collections.emptyList();
            }

            @Override
            public int getRenderedChunks() {
                return 150;
            }
        });
    }

    @AfterEach
    void tearDown() {
        if (bridgeBefore != null) {
            LadsGameBridge.set(bridgeBefore);
        }
    }

    @Test
    void testF1914AssertionsStandalone() {
        // Assert requirement 1: RealPing aggregate
        assertEquals(15, RealPing.aggregate(15, 40));
        assertEquals(20, RealPing.aggregate(20, -1));
        assertEquals(30, RealPing.aggregate(-1, 30));
        assertEquals(RealPing.NONE, RealPing.aggregate(-1, -1));

        // Assert requirement 7: HypixelTabFill
        List<String> completions = HypixelTabFill.complete("/play bedwars_e", Collections.emptyList());
        assertTrue(completions.contains("bedwars_eight_one"));

        // Assert requirement 4: Cloud Height MAX
        CloudHeightModule ch = (CloudHeightModule) ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
        assertNotNull(ch);
        assertTrue(ch.isEnabled());
        assertEquals(256.0f, CloudHeightModule.resolveHeight(CloudHeightModule.MAX_VALUE, 256));
        assertEquals(320.0f, CloudHeightModule.resolveHeight(CloudHeightModule.MAX_VALUE, 320));
        assertEquals(140.0f, CloudHeightModule.resolveHeight(140.0, 320));

        // Assert requirement 8: Mob kill banner option default ON & smooth scroll factor 1.5
        KillBannerModule kb = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
        assertNotNull(kb);
        assertTrue(((BoolOption) kb.getOption("Mobs")).get());
        assertEquals(1.5, SmoothScroll.FACTOR);
    }

    @Test
    void testF1914QaRoutineRunsFullyWith189Driver() {
        F1914Qa.Driver driver = new F1914Qa.Driver() {
            @Override public void screenshot(String name) { screenshotsTaken.add(name); }
            @Override public void cropArmorHud(String sourceShot, String destShot) { screenshotsTaken.add(destShot); }
            @Override public void setPlayerDiamondArmor() { playerArmorEquipped = true; }
            @Override public void restorePlayerArmor() { playerArmorEquipped = false; }
            @Override public void setCamera(int view, float pitch) {}
            @Override public void summonArmorStand(String name, double distance) { armorStandSummoned = true; }
            @Override public void removeArmorStand() { armorStandSummoned = false; }
            @Override public void openQuickplay() { screensOpened.add("Quickplay"); }
            @Override public void openHudEditor() { screensOpened.add("HudEditor"); }
            @Override public void openOptifine(String page) { screensOpened.add("OF-" + page); }
            @Override public void closeScreen() { screensOpened.add("Close"); }
            @Override public void setClouds(boolean on) {}
            @Override public void setPlayerYAndPitch(double y, float pitch) {}
            @Override public void showBossBar(String name) {}
            @Override public void clearBossBar() {}
            @Override public boolean is189() { return true; }
            @Override public int getWorldMaxHeight() { return 256; }
            @Override public void done(int passed, int failed) {
                completed = true;
                finalPassed = passed;
                finalFailed = failed;
            }
        };

        F1914Qa qa = new F1914Qa();
        for (int i = 0; i < 300 && !completed; i++) {
            qa.tick(driver);
        }

        assertTrue(completed, "F1914Qa routine should complete");
        assertEquals(0, finalFailed, "No failures expected in mock run: " + qa.getFailures());
        assertTrue(finalPassed >= 10, "Expected at least 10 passed checks, got " + finalPassed);

        // Verify screenshots were requested
        assertTrue(screenshotsTaken.contains("keystrokes-clean-global"));
        assertTrue(screenshotsTaken.contains("keystrokes-clean-custom"));
        assertTrue(screenshotsTaken.contains("keystrokes-grouped"));
        assertTrue(screenshotsTaken.contains("armor-hud"));
        assertTrue(screenshotsTaken.contains("armor-hud-crop"));
        assertTrue(screenshotsTaken.contains("coords-c"));
        assertTrue(screenshotsTaken.contains("clouds-max"));
        assertTrue(screenshotsTaken.contains("clouds-140"));
        assertTrue(screenshotsTaken.contains("bossbar-red"));
        assertTrue(screenshotsTaken.contains("bossbar-green"));
        assertTrue(screenshotsTaken.contains("bossbar-vanilla"));
        assertTrue(screenshotsTaken.contains("nametag-front"));
        assertTrue(screenshotsTaken.contains("nametag-back"));
        assertTrue(screenshotsTaken.contains("quickplay"));
        assertTrue(screenshotsTaken.contains("hypixel-huds"));
        assertTrue(screenshotsTaken.contains("of-details"));
        assertTrue(screenshotsTaken.contains("of-animations"));
        assertTrue(screenshotsTaken.contains("of-quality"));
        assertTrue(screenshotsTaken.contains("of-performance"));
        assertTrue(screenshotsTaken.contains("of-other"));
    }
}
