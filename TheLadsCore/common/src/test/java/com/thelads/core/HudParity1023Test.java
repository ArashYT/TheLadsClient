package com.thelads.core;

import com.thelads.core.client.*;
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
        GappleTracker.get().reset();
        InventoryDiffTracker.get().reset();
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

        // Air swings and accuracy
        tracker.onAirSwing();
        tracker.onAirSwing();
        // 2 hits, 2 air swings -> 50%
        assertEquals(50.0f, tracker.getAccuracy(), 0.01f);

        // Gapples
        tracker.onGapple();
        assertEquals(1, tracker.getGapplesUsed());
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
    void testBlockTrackerHeldBlockPlacement() {
        BlockTracker tracker = BlockTracker.get();
        tracker.reset();

        // 1. Player presses use on block at tick 10 with 64 cobblestone in slot 0
        tracker.onUseKeyOnBlock(10);
        tracker.checkHeldBlockTick(10, 0, true, 64);

        // At tick 11 (within 2 ticks), stack size drops to 63 -> 1 placed block counted
        tracker.checkHeldBlockTick(11, 0, true, 63);
        assertEquals(1, tracker.getBlocksPlaced());

        // At tick 15 (> 2 ticks since use on block at tick 10), count drops to 62 -> not counted as placed
        tracker.checkHeldBlockTick(15, 0, true, 62);
        assertEquals(1, tracker.getBlocksPlaced());

        // At tick 20, player uses key on block with 62 cobblestone in slot 0, then switches to slot 1 with 30 stone
        tracker.onUseKeyOnBlock(20);
        tracker.checkHeldBlockTick(20, 0, true, 62);
        tracker.checkHeldBlockTick(21, 1, true, 30); // slot changed!
        assertEquals(1, tracker.getBlocksPlaced()); // not counted as placed

        // In slot 1, stack drops from 30 to 29 at tick 22 (within 2 ticks of 20) -> counted!
        tracker.checkHeldBlockTick(22, 1, true, 29);
        assertEquals(2, tracker.getBlocksPlaced());
    }

    @Test
    void testGappleTracker() {
        CombatTracker ct = CombatTracker.get();
        GappleTracker gt = GappleTracker.get();
        ct.reset();
        gt.reset();
        assertEquals(0, ct.getGapplesUsed());

        // 1. Cancelled eat / right-click tap: using for 1 tick with 16 gapples
        gt.onTick(true, true, 0, 16, 1, 32);
        // Release right click immediately: isUsing=false, count is still 16 -> NOT counted!
        gt.onTick(false, true, 0, 16, 0, 32);
        assertEquals(0, ct.getGapplesUsed());

        // 2. Cancelled eat midway: using for 15 ticks, then release
        gt.onTick(true, true, 0, 16, 15, 32);
        gt.onTick(false, true, 0, 16, 0, 32);
        assertEquals(0, ct.getGapplesUsed());

        // 3. Normal eat finishes: used for 32 ticks, then count drops to 15
        gt.onTick(true, true, 0, 16, 32, 32);
        gt.onTick(false, true, 0, 15, 0, 32);
        assertEquals(1, ct.getGapplesUsed());

        // 4. Eat last gapple in stack (1 -> 0): used for 32 ticks, finishes with 0 count
        gt.onTick(true, true, 0, 1, 32, 32);
        gt.onTick(false, false, 0, 0, 0, 32);
        assertEquals(2, ct.getGapplesUsed());

        // 5. Off-hand eat finishes (slot 36): starts at 16 gapples in off-hand
        gt.onTick(true, true, 36, 16, 32, 32);
        // Finishing tick: count drops to 15 in off-hand (slot 36)
        gt.onTick(false, true, 36, 15, 0, 32);
        assertEquals(3, ct.getGapplesUsed());

        // 6. Off-hand eat cancelled: starts at 15 gapples in off-hand, cancelled after 10 ticks
        gt.onTick(true, true, 36, 15, 10, 32);
        gt.onTick(false, true, 36, 15, 0, 32);
        assertEquals(3, ct.getGapplesUsed());

        // 7. Off-hand eat last gapple in stack (1 -> 0)
        gt.onTick(true, true, 36, 1, 32, 32);
        gt.onTick(false, false, 36, 0, 0, 32);
        assertEquals(4, ct.getGapplesUsed());
    }

    @Test
    void testInventoryDiffTracker() {
        InventoryDiffTracker diff = InventoryDiffTracker.get();
        diff.reset();

        // 1. Initial snapshot
        diff.beginScan(false);
        diff.addScannedItem(1, 64, "Cobblestone");
        diff.endScan();

        // 2. Pickup 5 Cobblestone (64 -> 69) and 1 Diamond
        diff.beginScan(false);
        diff.addScannedItem(1, 69, "Cobblestone");
        diff.addScannedItem(264, 1, "Diamond");
        diff.endScan();

        // 3. Container open (moving items between chest/inv): should keep tracker at no baseline
        diff.beginScan(true);
        diff.endScan();

        // 4. Container closes: first scan after closing sets baseline only, no records emitted!
        diff.beginScan(false);
        diff.addScannedItem(1, 69, "Cobblestone");
        diff.addScannedItem(264, 1, "Diamond");
        diff.endScan();
    }

    @Test
    void testHitIndicatorAngleMath() {
        // Attacker on left (relative yaw = 0.0) -> clock angle = -90 (or 270 deg)
        assertEquals(-90.0, HitIndicatorHudElement.calculateIndicatorAngle(0.0f), 0.001);

        // Attacker in front (relative yaw = 90.0) -> clock angle = 0.0 (12 o'clock)
        assertEquals(0.0, HitIndicatorHudElement.calculateIndicatorAngle(90.0f), 0.001);

        // Attacker on right (relative yaw = 180.0) -> clock angle = 90.0 (3 o'clock)
        assertEquals(90.0, HitIndicatorHudElement.calculateIndicatorAngle(180.0f), 0.001);

        // Attacker behind (relative yaw = 270.0) -> clock angle = 180.0 (6 o'clock)
        assertEquals(180.0, HitIndicatorHudElement.calculateIndicatorAngle(270.0f), 0.001);
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
        element.renderEditor(g);
        element.render(g);
    }

    @Test
    void testPvPInfoHudElement() {
        PvPInfoHudElement element = new PvPInfoHudElement();
        element.setModuleName("PvPInfo");
        TestGraphics g = new TestGraphics();

        CombatTracker.get().onAttack(10, System.nanoTime());
        CombatTracker.get().onHurt(10, false, System.nanoTime());
        CombatTracker.get().onAirSwing();

        element.prepareRender(g, true);
        element.renderEditor(g);
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

        ItemTrackerHudElement.recordChange("Cobblestone", 16);
        ItemTrackerHudElement.recordChange("Ender Pearl", -1);

        element.prepareRender(g, true);
        element.renderEditor(g);
        element.render(g);
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
    void testCooldownHudElementAvailability() {
        CooldownHudElement element = new CooldownHudElement();
        assertTrue(element.isAvailable());

        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public boolean hasItemCooldowns() { return false; }
        });
        assertFalse(element.isAvailable());
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
    void testFPSHudElementExtendedStats() {
        FPSHudElement element = new FPSHudElement();
        element.setModuleName("FPS");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
        assertNotNull(element);
    }

    @Test
    void testSystemResourcesHudElement() {
        SystemResourcesHudElement element = new SystemResourcesHudElement();
        element.setModuleName("SystemResources");
        TestGraphics g = new TestGraphics();

        element.prepareRender(g, true);
        element.render(g);
        assertTrue(SystemMetrics.getCpuLoad() >= -1.0);
    }

    @Test
    void cpuLoadIsRealOrNa() throws InterruptedException {
        // HotSpot has com.sun.management: a real reading (the first one may still be warming up), never -1 for good.
        double load = SystemMetrics.getCpuLoad();
        for (int i = 0; i < 10 && load < 0; i++) {
            Thread.sleep(100);
            load = SystemMetrics.getCpuLoad();
        }
        assertTrue(load >= 0 && load <= 100, "CPU load " + load);

        // A JVM that cannot tell: "n/a", not a made-up number.
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public double getCpuLoad() { return -1; }
        });
        SystemResourcesHudElement element = new SystemResourcesHudElement();
        element.setModuleName("SystemResources");
        TestGraphics g = new TestGraphics();
        element.prepareRender(g, false);
        element.render(g);
        assertTrue(g.drawCalls.stream().anyMatch(c -> c.contains("CPU: n/a")), g.drawCalls.toString());
        assertTrue(g.drawCalls.stream().noneMatch(c -> c.contains("CPU: 15%")));
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
        element.render(g);
    }

    @Test
    void testCompassStripNegativeYaw() {
        assertEquals(315.0f, CompassStripHudElement.normalizeYaw(-45.0f), 0.001f);
        assertEquals(180.0f, CompassStripHudElement.normalizeYaw(-180.0f), 0.001f);
        assertEquals(0.0f, CompassStripHudElement.normalizeYaw(-360.0f), 0.001f);
        assertEquals(320.0f, CompassStripHudElement.normalizeYaw(-400.0f), 0.001f);
        assertEquals(0.0f, CompassStripHudElement.normalizeYaw(0.0f), 0.001f);
        assertEquals(90.0f, CompassStripHudElement.normalizeYaw(90.0f), 0.001f);
        assertEquals(180.0f, CompassStripHudElement.normalizeYaw(Float.NaN), 0.001f);
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
        element.render(g);
    }

    @Test
    void testBlockCounterHudElement() {
        BlockCounterHudElement element = new BlockCounterHudElement();
        element.setModuleName("BlockCounter");
        TestGraphics g = new TestGraphics();

        BlockTracker.get().onBlockPlaced();
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
    void testHeartsAbsorptionTiersAt20And40() {
        // Absorption = 20 HP (10 full absorption hearts, tier 0 = gold)
        assertEquals(10, HeartsHudElement.calculateAbsorptionHearts(20.0f));
        assertEquals(0, HeartsHudElement.calculateAbsorptionTier(20.0f));

        // Absorption = 40 HP (20 full absorption hearts, tier 1 = orange)
        assertEquals(10, HeartsHudElement.calculateAbsorptionHearts(40.0f));
        assertEquals(1, HeartsHudElement.calculateAbsorptionTier(40.0f));

        // Absorption = 22 HP (11 hearts -> 1 heart in tier 1 = orange)
        assertEquals(1, HeartsHudElement.calculateAbsorptionHearts(22.0f));
        assertEquals(1, HeartsHudElement.calculateAbsorptionTier(22.0f));

        // Absorption = 4 HP (2 hearts in tier 0 = gold)
        assertEquals(2, HeartsHudElement.calculateAbsorptionHearts(4.0f));
        assertEquals(0, HeartsHudElement.calculateAbsorptionTier(4.0f));
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
    }
}
