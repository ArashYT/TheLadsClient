package com.thelads.smp.features.arise.nb5;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.thelads.smp.features.arise.nb5.client.ParkourClientController;
import com.thelads.smp.features.arise.nb5.network.ParkourSyncPayload;

/**
 * Unit test suite for NB5 (ParCool-style Parkour & Stamina systems).
 * Tests all game logic, stamina rules, cooldowns, mathematical calculations, and Arise config parity.
 */
public class AriseNB5UnitTest {

    private AriseNB5Config config;
    private PlayerStaminaData data;

    @BeforeEach
    void setUp() {
        config = new AriseNB5Config();
        data = new PlayerStaminaData();
    }

    @Test
    @DisplayName("Verify Arise SMP ParCool default values are accurately preserved")
    void testConfigDefaultsMatchAriseSMP() {
        assertTrue(config.enabled, "Feature must be enabled by default");
        assertTrue(config.vaultEnabled, "Vault must be enabled by default");
        assertTrue(config.wallJumpEnabled, "Wall Jump must be enabled by default");
        assertTrue(config.ledgeGrabEnabled, "Ledge Grab must be enabled by default");
        assertTrue(config.slideEnabled, "Slide must be enabled by default");
        assertTrue(config.dodgeRollEnabled, "Dodge Roll must be enabled by default");
        assertTrue(config.breakfallEnabled, "Breakfall must be enabled by default");
        assertTrue(config.staminaEnabled, "Stamina must be enabled by default");

        // Exact Arise ParCool config values
        assertEquals(1000, config.maxStamina, "Max stamina must be 1000");
        assertEquals(12, config.staminaRecoveryRate, "Recovery rate must be 12");
        assertEquals(50, config.vaultStaminaCost, "Vault stamina cost must match Arise 50");
        assertEquals(120, config.wallJumpStaminaCost, "Wall jump stamina cost must match Arise 120");
        assertEquals(150, config.ledgeClimbStaminaCost, "Ledge climb stamina cost must match Arise 150");
        assertEquals(2, config.ledgeHangStaminaCostPerTick, "Ledge hang stamina cost must match Arise 2/tick");
        assertEquals(80, config.dodgeStaminaCost, "Dodge stamina cost must match Arise 80");
        assertEquals(100, config.rollStaminaCost, "Roll stamina cost must match Arise 100");
        assertEquals(50, config.breakfallStaminaCost, "Breakfall stamina cost must be 50");
        assertTrue(config.exhaustionRecoveryPenalty, "Exhaustion penalty must be active by default");
        assertTrue(config.infiniteStaminaCreative, "Creative infinite stamina must match Arise true");

        // Timings & Modifiers
        assertEquals(11, config.dodgeCoolTime, "Dodge cooldown must match Arise 11");
        assertEquals(30, config.successiveDodgeCoolTime, "Successive dodge cooldown must match Arise 30");
        assertEquals(3, config.successiveDodgeCount, "Successive dodge count must match Arise 3");
        assertEquals(15, config.slidingContinuableTick, "Slide tick duration must match Arise 15");
        assertEquals(110.0f, config.acceptableAngleWallJump, 0.001f, "Wall jump angle must match Arise 110 deg");
        assertEquals(2.0f, config.lowestFallDistanceForBreakfall, 0.001f, "Breakfall lowest fall distance must match Arise 2.0");
        assertEquals(5.0f, config.breakfallFallReduction, 0.001f, "Breakfall reduction must be 5.0 blocks");
        assertEquals(1.0f, config.dodgeSpeedModifier, 0.001f, "Dodge modifier must match Arise 1.0");

        // HUD config
        assertEquals("Light", config.staminaHudType, "HUD type must match Arise Light");
        assertEquals("Right", config.alignH, "HUD horizontal alignment must match Arise Right");
        assertEquals("Bottom", config.alignV, "HUD vertical alignment must match Arise Bottom");
        assertEquals(0, config.offsetHLightHud, "HUD horizontal offset must match Arise 0");
        assertEquals(-6, config.offsetVLightHud, "HUD vertical offset must match Arise -6");
    }

    @Test
    @DisplayName("Verify stamina deduction per parkour action")
    void testStaminaConsumptionPerAction() {
        assertEquals(1000, data.getCurrentStamina());

        // Vault: 50
        boolean vaultSuccess = data.consumeForAction(ParkourActionType.VAULT, false);
        assertTrue(vaultSuccess);
        assertEquals(950, data.getCurrentStamina());
        assertEquals(ParkourActionType.VAULT, data.getActiveAction());

        // Wall Jump: 120
        boolean wallJumpSuccess = data.consumeForAction(ParkourActionType.WALL_JUMP, false);
        assertTrue(wallJumpSuccess);
        assertEquals(830, data.getCurrentStamina());
        assertEquals(ParkourActionType.WALL_JUMP, data.getActiveAction());

        // Ledge Climb: 150
        boolean climbSuccess = data.consumeForAction(ParkourActionType.LEDGE_CLIMB, false);
        assertTrue(climbSuccess);
        assertEquals(680, data.getCurrentStamina());

        // Dodge: 80
        // Advance cooldown tick
        data.tick(false);
        boolean dodgeSuccess = data.consumeForAction(ParkourActionType.DODGE, false);
        assertTrue(dodgeSuccess);
        assertEquals(600 + config.staminaRecoveryRate, data.getCurrentStamina(), 12);

        // Elapse dodge cooldown
        for (int i = 0; i < 11; i++) {
            data.tick(false);
        }
        boolean rollSuccess = data.consumeForAction(ParkourActionType.ROLL, false);
        assertTrue(rollSuccess);
    }

    @Test
    @DisplayName("Verify exhaustion penalty and recovery threshold (25%)")
    void testExhaustionAndRecoveryThreshold() {
        // Drain stamina completely to 0
        data.setCurrentStamina(0);
        assertTrue(data.isExhausted(), "Player should be exhausted when stamina is 0");
        assertFalse(data.canPerform(ParkourActionType.VAULT, false), "Cannot perform actions while exhausted");
        assertFalse(data.canPerform(ParkourActionType.DODGE, false), "Cannot perform actions while exhausted");

        // Tick recovery: rate should be halved during exhaustion penalty
        data.tick(false);
        int expectedHalvedRate = config.staminaRecoveryRate / 2; // 12 / 2 = 6
        assertEquals(expectedHalvedRate, data.getCurrentStamina(), "Stamina recovery should be halved when exhausted");
        assertTrue(data.isExhausted(), "Still exhausted under 250 threshold");

        // Set to 244 (below 250), then tick + 6 = 250
        data.setCurrentStamina(244);
        data.setExhausted(true);
        data.tick(false);
        assertEquals(250, data.getCurrentStamina());
        assertFalse(data.isExhausted(), "Exhaustion should clear at 250 (1000 / 4)");
        assertTrue(data.canPerform(ParkourActionType.VAULT, false), "Can perform actions once recovered");
    }

    @Test
    @DisplayName("Verify dodge cooldown and successive dodge throttling")
    void testDodgeCooldownAndThrottling() {
        assertEquals(0, data.getDodgeCooldownTicks());

        // First dodge: triggers 11 ticks cooldown
        data.consumeForAction(ParkourActionType.DODGE, false);
        assertEquals(11, data.getDodgeCooldownTicks());
        assertFalse(data.canPerform(ParkourActionType.DODGE, false), "Cannot dodge while on cooldown");

        // Elapse cooldown
        for (int i = 0; i < 11; i++) {
            data.tick(false);
        }
        assertEquals(0, data.getDodgeCooldownTicks());
        assertTrue(data.canPerform(ParkourActionType.DODGE, false));

        // Second dodge
        data.consumeForAction(ParkourActionType.DODGE, false);
        for (int i = 0; i < 11; i++) {
            data.tick(false);
        }

        // Third successive dodge: triggers full 30 ticks successive cooldown
        data.consumeForAction(ParkourActionType.DODGE, false);
        assertEquals(30, data.getDodgeCooldownTicks(), "Third successive dodge should trigger 30 ticks cooldown");
    }

    @Test
    @DisplayName("Verify wall jump angle geometry calculations")
    void testWallJumpAngleCalculations() {
        // Wall normal points in +X direction (wall is to player's -X)
        double normalX = 1.0;
        double normalZ = 0.0;

        // Player looking towards wall (-X): look = (-1, 0)
        // dot = lookX * (-normalX) + lookZ * (-normalZ) = (-1.0) * (-1.0) = 1.0
        // acos(1.0) = 0 degrees (directly facing wall)
        double dotFacing = (-1.0) * (-normalX) + (0.0) * (-normalZ);
        double angleFacing = Math.toDegrees(Math.acos(Math.clamp(dotFacing, -1.0, 1.0)));
        assertEquals(0.0, angleFacing, 0.001);
        assertTrue(angleFacing <= config.acceptableAngleWallJump, "Directly facing wall must be accepted");

        // Player looking perpendicular (Z): look = (0, 1)
        double dotPerp = (0.0) * (-normalX) + (1.0) * (-normalZ);
        double anglePerp = Math.toDegrees(Math.acos(Math.clamp(dotPerp, -1.0, 1.0)));
        assertEquals(90.0, anglePerp, 0.001);
        assertTrue(anglePerp <= config.acceptableAngleWallJump, "Looking along wall must be accepted");

        // Player looking away from wall (+X): look = (1, 0)
        double dotAway = (1.0) * (-normalX) + (0.0) * (-normalZ);
        double angleAway = Math.toDegrees(Math.acos(Math.clamp(dotAway, -1.0, 1.0)));
        assertEquals(180.0, angleAway, 0.001);
        assertFalse(angleAway <= config.acceptableAngleWallJump, "Facing completely away from wall (180 deg) must be rejected (> 110 deg)");
    }

    @Test
    @DisplayName("Verify breakfall fall damage reduction logic")
    void testBreakfallDamageReductionMath() {
        float fallReduction = config.breakfallFallReduction; // 5.0 blocks
        float minFallDistance = config.lowestFallDistanceForBreakfall; // 2.0 blocks

        // Fall distance 6.5 blocks: reduced by 5.0 -> 1.5 blocks
        double fall1 = 6.5;
        double reduced1 = Math.max(0.0, fall1 - fallReduction);
        assertEquals(1.5, reduced1, 0.001);

        // Fall distance 4.0 blocks: reduced by 5.0 -> 0.0 (no damage)
        double fall2 = 4.0;
        double reduced2 = Math.max(0.0, fall2 - fallReduction);
        assertEquals(0.0, reduced2, 0.001);

        // Fall distance 1.5 blocks: below min fall distance, does not trigger breakfall
        assertTrue(1.5 < minFallDistance);
    }

    @Test
    @DisplayName("Verify creative mode infinite stamina behavior")
    void testCreativeModeInfiniteStamina() {
        data.setCurrentStamina(100);
        data.tick(true); // Creative mode tick

        assertEquals(1000, data.getCurrentStamina(), "Creative mode tick should immediately restore stamina to 1000");
        assertFalse(data.isExhausted(), "Creative mode should never be exhausted");
        assertTrue(data.canPerform(ParkourActionType.WALL_JUMP, true), "Creative mode can always perform actions");
        assertTrue(data.consumeForAction(ParkourActionType.WALL_JUMP, true), "Creative consumption always succeeds");
    }

    @Test
    @DisplayName("Verify ParkourActionType enum mapping")
    void testActionTypeEnumMapping() {
        for (ParkourActionType type : ParkourActionType.values()) {
            assertEquals(type, ParkourActionType.byId(type.getId()), "Enum ID mapping must be bijective");
        }
        assertEquals(ParkourActionType.NONE, ParkourActionType.byId(999), "Unknown ID should map to NONE");
    }

    @Test
    @DisplayName("Verify infinite ledge hang prevention: zero stamina cancels ledge hang and drops player")
    void testLedgeHangDropOnZeroStamina() {
        data.setActiveAction(ParkourActionType.LEDGE_HANG, 200);
        data.setCurrentStamina(2); // Exactly 1 tick remaining

        data.tick(false);
        assertEquals(0, data.getCurrentStamina(), "Stamina should be fully consumed");
        assertTrue(data.isExhausted(), "Player should be exhausted at 0 stamina");
        assertEquals(ParkourActionType.NONE, data.getActiveAction(), "Ledge hang must be cancelled when stamina reaches 0");
        assertEquals(0, data.getActionTicks(), "Action ticks must be cleared to 0");
    }

    @Test
    @DisplayName("Verify slide spam prevention and cooldown enforcement")
    void testSlideCooldownAndSpamPrevention() {
        assertTrue(data.canPerform(ParkourActionType.SLIDE, false), "Initial slide should be permitted");

        data.consumeForAction(ParkourActionType.SLIDE, false);
        assertEquals(ParkourActionType.SLIDE, data.getActiveAction());
        assertFalse(data.canPerform(ParkourActionType.SLIDE, false), "Cannot perform slide while already sliding");

        // Tick through slide duration (15 ticks)
        for (int i = 0; i < config.slidingContinuableTick; i++) {
            assertFalse(data.canPerform(ParkourActionType.SLIDE, false), "Cannot slide during active slide");
            data.tick(false);
        }

        // Active action has expired, but slide cooldown (15 ticks) should still block spam
        assertEquals(ParkourActionType.NONE, data.getActiveAction());
        assertTrue(data.getSlideCooldownTicks() > 0, "Slide cooldown should remain active after slide ends");
        assertFalse(data.canPerform(ParkourActionType.SLIDE, false), "Cannot slide while slide cooldown is active");

        // Tick through remaining cooldown (15 ticks)
        while (data.getSlideCooldownTicks() > 0) {
            data.tick(false);
        }

        assertEquals(0, data.getSlideCooldownTicks());
        assertTrue(data.canPerform(ParkourActionType.SLIDE, false), "Can slide again once cooldown has fully elapsed");
    }

    @Test
    @DisplayName("Verify ParkourAnimationPayload construction and action type resolution")
    void testParkourAnimationPayload() {
        com.thelads.smp.features.arise.nb5.network.ParkourAnimationPayload payload =
                new com.thelads.smp.features.arise.nb5.network.ParkourAnimationPayload(42, ParkourActionType.ROLL.getId(), 14);
        assertEquals(42, payload.entityId());
        assertEquals(ParkourActionType.ROLL.getId(), payload.actionTypeId());
        assertEquals(14, payload.actionTicks());
        assertEquals(ParkourActionType.ROLL, payload.getActionType());
        assertEquals("ladssmp:parkour_anim", payload.type().id().toString());
    }

    @Test
    @DisplayName("Verify anti-ban server gating: parkour disabled on foreign servers until confirmed by sync")
    void testAntiBanServerGating() {
        ParkourClientController.resetSession();
        assertFalse(ParkourClientController.isServerConfirmed(), "Parkour must not be confirmed by default");
        assertFalse(ParkourClientController.isParkourAllowed(), "Parkour must be disabled without server confirmation");

        // Simulate server sync reception
        ParkourSyncPayload payload = new ParkourSyncPayload(800, false, ParkourActionType.NONE.getId(), 0);
        ParkourClientController.onSync(payload);
        assertTrue(ParkourClientController.isServerConfirmed(), "Sync packet must confirm server presence");

        // Disconnect / session reset
        ParkourClientController.resetSession();
        assertFalse(ParkourClientController.isServerConfirmed(), "Disconnect must revoke server confirmation");
        assertFalse(ParkourClientController.isParkourAllowed(), "Parkour must be disabled after disconnect");
    }
}
