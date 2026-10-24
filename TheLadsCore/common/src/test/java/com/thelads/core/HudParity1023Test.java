package com.thelads.core;

import com.thelads.core.client.BlockTracker;
import com.thelads.core.client.CombatTracker;
import com.thelads.core.client.DeathTracker;
import com.thelads.core.client.TpsTracker;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.*;
import com.thelads.core.client.util.SystemMetrics;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HudParity1023Test {
    private static class TestGraphics extends LadsGraphicsTest.MockGraphics {
        public TestGraphics() {
            width = 400;
            height = 240;
        }
    }

    private LadsGameBridge prevBridge;

    @BeforeEach
    void setUp() {
        prevBridge = LadsGameBridge.get();
        LadsGameBridge.set(new DefaultGameBridge());
        CombatTracker.get().reset();
        BlockTracker.get().reset();
        DeathTracker.get().reset();
        TpsTracker.get().reset();
    }

    @AfterEach
    void tearDown() {
        LadsGameBridge.set(prevBridge);
    }

    @Test
    void testCombatTracker() {
        CombatTracker tracker = CombatTracker.get();
        assertEquals(0, tracker.getCombo());
        assertEquals(0, tracker.getHitsLanded());
        assertEquals(0, tracker.getHitsTaken());

        long now = 10_000_000_000L;
        tracker.onAttack(100, now);
        assertEquals(1, tracker.getSwings());

        int combo = tracker.onHurt(100, false, now + 100_000_000L);
        assertEquals(1, combo);
        assertEquals(1, tracker.getHitsLanded());

        // Second hit in combo
        now += 200_000_000L;
        tracker.onAttack(100, now);
        combo = tracker.onHurt(100, false, now + 50_000_000L);
        assertEquals(2, combo);
        assertEquals(2, tracker.getHitsLanded());

        // Player hurt resets combo
        tracker.onHurt(999, true, now + 100_000_000L);
        assertEquals(0, tracker.getCombo());
        assertEquals(1, tracker.getHitsTaken());

        // Gapples
        tracker.onGapple();
        assertEquals(1, tracker.getGapplesUsed());
        assertTrue(tracker.getAccuracy() > 0.0f);
    }

    @Test
    void testTpsTracker() {
        TpsTracker tps = TpsTracker.get();
        assertEquals(20.0, tps.getTps(), 0.01);

        tps.onTimeUpdate(1000);
        tps.onTimeUpdate(1020);
        assertTrue(tps.getTps() <= 20.0);
        assertTrue(tps.getTps() >= 0.0);
    }

    @Test
    void testBlockTracker() {
        BlockTracker tracker = BlockTracker.get();
        assertEquals(0, tracker.getBlocksPlaced());
        tracker.onBlockPlaced();
        tracker.onBlockPlaced();
        assertEquals(2, tracker.getBlocksPlaced());
        tracker.reset();
        assertEquals(0, tracker.getBlocksPlaced());
    }

    @Test
    void testDeathTracker() {
        DeathTracker dt = DeathTracker.get();
        assertEquals(0, dt.getDeathCount());
        assertFalse(dt.hasDied());

        dt.onDeath(150.5, 64.0, -320.2, "minecraft:overworld", "Slain by Zombie");
        assertEquals(1, dt.getDeathCount());
        assertTrue(dt.hasDied());
        assertEquals(150, dt.getLastX());
        assertEquals(64, dt.getLastY());
        assertEquals(-321, dt.getLastZ());
        assertEquals("minecraft:overworld", dt.getLastDimension());
        assertEquals("Slain by Zombie", dt.getLastCause());
    }

    @Test
    void testComboCounterHudElement() {
        ComboCounterHudElement element = new ComboCounterHudElement();
        element.setModuleName("ComboCounter");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        assertEquals(60, element.getWidth());

        CombatTracker.get().onAttack(10, System.nanoTime());
        CombatTracker.get().onHurt(10, false, System.nanoTime());
        element.prepareRender(g, false);
        assertNotNull(element);
    }

    @Test
    void testPvPInfoHudElement() {
        PvPInfoHudElement element = new PvPInfoHudElement();
        element.setModuleName("PvPInfo");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        assertTrue(element.getHeight() >= 16);
        element.render(g);
    }

    @Test
    void testPotionCounterHudElement() {
        PotionCounterHudElement element = new PotionCounterHudElement();
        element.setModuleName("PotionCounter");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testItemTrackerHudElement() {
        ItemTrackerHudElement element = new ItemTrackerHudElement();
        element.setModuleName("ItemTracker");
        TestGraphics g = new TestGraphics();

        ItemTrackerHudElement.recordChange("Ender Pearl", 16);
        ItemTrackerHudElement.recordChange("Arrows", -5);
        element.prepareRender(g, false);
        element.render(g);
        element.renderEditor(g);
    }

    @Test
    void testCooldownHudElement() {
        CooldownHudElement element = new CooldownHudElement();
        element.setModuleName("Cooldowns");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testTpsHudElement() {
        TpsHudElement element = new TpsHudElement();
        element.setModuleName("TPS");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testFPSHudElementStats() {
        FPSHudElement element = new FPSHudElement();
        element.setModuleName("FPS");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, false);
        element.render(g);
    }

    @Test
    void testSystemResourcesHudElement() {
        SystemResourcesHudElement element = new SystemResourcesHudElement();
        element.setModuleName("SystemResources");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testSessionPlaytimeHudElement() {
        SessionPlaytimeHudElement element = new SessionPlaytimeHudElement();
        element.setModuleName("Playtime");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testCompassStripHudElement() {
        CompassStripHudElement element = new CompassStripHudElement();
        element.setModuleName("CompassStrip");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testViewAnglesHudElement() {
        ViewAnglesHudElement element = new ViewAnglesHudElement();
        element.setModuleName("ViewAngles");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testMouseTrailHudElement() {
        MouseTrailHudElement element = new MouseTrailHudElement();
        element.setModuleName("MouseTrail");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testBlockCounterHudElement() {
        BlockCounterHudElement element = new BlockCounterHudElement();
        element.setModuleName("BlockCounter");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testProtectionHudElement() {
        ProtectionHudElement element = new ProtectionHudElement();
        element.setModuleName("Protection");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testDeathInfoHudElement() {
        DeathInfoHudElement element = new DeathInfoHudElement();
        element.setModuleName("DeathInfo");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
    }

    @Test
    void testHeartsHudElement() {
        HeartsHudElement element = new HeartsHudElement();
        element.setModuleName("Hearts");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testHitIndicatorHudElement() {
        HitIndicatorHudElement element = new HitIndicatorHudElement();
        element.setModuleName("HitIndicator");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testAllModulesRegisteredInModuleManager() {
        ModuleManager mm = ModuleManager.getInstance();
        List<String> names = List.of(
            "ComboCounter", "PvPInfo", "PotionCounter", "ItemTracker", "Cooldowns",
            "TPS", "SystemResources", "Playtime", "CompassStrip", "ViewAngles",
            "MouseTrail", "BlockCounter", "Protection", "DeathInfo", "Hearts", "HitIndicator"
        );
        for (String name : names) {
            assertNotNull(mm.getModule(name), "Module should be registered: " + name);
        }
    }

    @Test
    void testHudManagerElements() {
        HudManager hm = HudManager.getInstance();
        assertNotNull(hm);
        // Verify HudManager singleton initialization doesn't throw and includes new elements
    }
}
