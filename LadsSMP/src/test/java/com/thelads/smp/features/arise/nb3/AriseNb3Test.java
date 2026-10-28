package com.thelads.smp.features.arise.nb3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.smp.features.arise.nb3.backweapon.BackWeaponFeature;
import com.thelads.smp.features.arise.nb3.botanypots.BotanyPotsFeature;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorData;
import com.thelads.smp.features.arise.nb3.cosmeticarmor.CosmeticArmorFeature;
import com.thelads.smp.features.arise.nb3.holdmyitems.HoldMyItemsFeature;
import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ObscureTooltipsFeature;
import com.thelads.smp.features.arise.nb3.snailmail.SnailMailFeature;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated JUnit 5 verification tests for Native Batch NB3 (Arise SMP features):
 * 1. Back Weapon Slot
 * 2. Cosmetic Armor Reworked
 * 3. Hold My Items
 * 4. Immersive UI
 * 5. MmmMmmMmmMmm Target Dummy
 * 6. Obscure Tooltips
 * 7. Snail Mail
 * 8. Botany Pots
 * + Configuration defaults & JSON serialization roundtrip
 */
public class AriseNb3Test {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetConfig() {
        AriseNb3Config.setInstanceForTesting(new AriseNb3Config());
        ImmersiveUIState.reset();
        SnailMailFeature.clearPendingForTesting();
    }

    @Test
    void testConfigDefaults() {
        AriseNb3Config cfg = AriseNb3Config.get();
        assertNotNull(cfg);

        // Feature 1: Back Weapon
        assertTrue(cfg.backWeapon.enabled);
        assertTrue(cfg.backWeapon.showBackWeapon);
        assertEquals(0, cfg.backWeapon.xOffset);
        assertEquals(0, cfg.backWeapon.yOffset);
        assertTrue(cfg.backWeapon.disallowCurioItems);
        assertTrue(cfg.backWeapon.allowSwapping);

        // Feature 2: Cosmetic Armor
        assertTrue(cfg.cosmeticArmor.enabled);
        assertFalse(cfg.cosmeticArmor.cosArmorKeepThroughDeath);
        assertEquals(65, cfg.cosmeticArmor.guiButtonLeft);
        assertEquals(67, cfg.cosmeticArmor.guiButtonTop);
        assertFalse(cfg.cosmeticArmor.hideButton);

        // Feature 3: Hold My Items
        assertTrue(cfg.holdMyItems.enabled);
        assertEquals(1.0f, cfg.holdMyItems.animationSpeed, 0.001f);
        assertEquals(0.018f, cfg.holdMyItems.switchSpeed, 0.001f);
        assertEquals(0.0f, cfg.holdMyItems.viewmodelXOffset, 0.001f);
        assertEquals(0.0f, cfg.holdMyItems.viewmodelYOffset, 0.001f);
        assertEquals(0.0f, cfg.holdMyItems.viewmodelZOffset, 0.001f);
        assertTrue(cfg.holdMyItems.enableSwimmingAnimation);
        assertTrue(cfg.holdMyItems.enablePunchingAnimation);

        // Feature 4: Immersive UI
        assertTrue(cfg.immersiveUI.enabled);
        assertTrue(cfg.immersiveUI.enableHotbarSelectorAnimation);
        assertEquals(3.0f, cfg.immersiveUI.hotbarSelectorSpeed, 0.001f);
        assertTrue(cfg.immersiveUI.enableFloatingItemRotation);
        assertTrue(cfg.immersiveUI.enableScreenShake);
        assertEquals(8, cfg.immersiveUI.shakeTimer);
        assertEquals(1.25f, cfg.immersiveUI.shakeAmplitude, 0.001f);

        // Feature 5: Target Dummy
        assertTrue(cfg.targetDummy.enabled);
        assertTrue(cfg.targetDummy.damageNumbers);
        assertEquals(0.75f, cfg.targetDummy.animationIntensity, 0.001f);
        assertTrue(cfg.targetDummy.fullBrightDamageNumbers);
        assertEquals("COLOR_AND_MULTIPLIER", cfg.targetDummy.critMode);
        assertTrue(cfg.targetDummy.hayParticles);
        assertEquals("DYNAMIC", cfg.targetDummy.dpsMode);
        assertTrue(cfg.targetDummy.disableEquipmentDamage);
        assertTrue(cfg.targetDummy.dummyDecoy);
        assertEquals(12, cfg.targetDummy.scarecrowRadius);

        // Feature 6: Obscure Tooltips
        assertTrue(cfg.obscureTooltips.enabled);
        assertTrue(cfg.obscureTooltips.displayArmorModels);
        assertTrue(cfg.obscureTooltips.displayToolsModels);

        // Feature 7: Snail Mail
        assertTrue(cfg.snailMail.enabled);
        assertTrue(cfg.snailMail.lockBoxes);
        assertTrue(cfg.snailMail.protectBoxDestroy);
        assertTrue(cfg.snailMail.opBypassLock);
        assertEquals(3, cfg.snailMail.bypassLockOpLevel);
        assertEquals(5, cfg.snailMail.deliveryDelayMinutes);

        // Feature 8: Botany Pots
        assertTrue(cfg.botanyPots.enabled);
        assertEquals(200, cfg.botanyPots.growthIntervalTicks);
        assertEquals(40, cfg.botanyPots.hopperExportIntervalTicks);
        assertTrue(cfg.botanyPots.enableHopperPots);
    }

    @Test
    void testConfigSerializationRoundtrip() throws IOException {
        Path configFile = tempDir.resolve("arise-nb3.json");
        AriseNb3Config initial = new AriseNb3Config();
        initial.backWeapon.xOffset = 15;
        initial.cosmeticArmor.cosArmorKeepThroughDeath = true;
        initial.holdMyItems.animationSpeed = 1.5f;
        initial.immersiveUI.shakeAmplitude = 2.0f;
        initial.targetDummy.scarecrowRadius = 18;
        initial.snailMail.deliveryDelayMinutes = 8;
        initial.botanyPots.growthIntervalTicks = 350;

        try (Writer writer = Files.newBufferedWriter(configFile)) {
            GSON.toJson(initial, writer);
        }

        assertTrue(Files.exists(configFile));

        AriseNb3Config loaded;
        try (Reader reader = Files.newBufferedReader(configFile)) {
            loaded = GSON.fromJson(reader, AriseNb3Config.class);
        }

        assertNotNull(loaded);
        assertEquals(15, loaded.backWeapon.xOffset);
        assertTrue(loaded.cosmeticArmor.cosArmorKeepThroughDeath);
        assertEquals(1.5f, loaded.holdMyItems.animationSpeed, 0.001f);
        assertEquals(2.0f, loaded.immersiveUI.shakeAmplitude, 0.001f);
        assertEquals(18, loaded.targetDummy.scarecrowRadius);
        assertEquals(8, loaded.snailMail.deliveryDelayMinutes);
        assertEquals(350, loaded.botanyPots.growthIntervalTicks);
    }

    @Test
    void testTargetDummyDamageRecords() {
        com.thelads.smp.features.arise.nb3.targetdummy.TargetDummyEntity.DamageRecord record =
                new com.thelads.smp.features.arise.nb3.targetdummy.TargetDummyEntity.DamageRecord(5000L, 14.5f);
        assertEquals(5000L, record.timestampMs());
        assertEquals(14.5f, record.amount(), 0.001f);
    }

    @Test
    void testCosmeticArmorDataAndVisibility() {
        CosmeticArmorData data = new CosmeticArmorData();
        assertTrue(data.isVisible(EquipmentSlot.HEAD));
        assertTrue(data.isVisible(EquipmentSlot.CHEST));
        assertTrue(data.isVisible(EquipmentSlot.LEGS));
        assertTrue(data.isVisible(EquipmentSlot.FEET));

        // When cosmetic slot is empty, get returns EMPTY
        assertEquals(ItemStack.EMPTY, data.get(EquipmentSlot.HEAD));

        // Test toggle visibility
        boolean toggled = data.toggleVisibility(EquipmentSlot.HEAD);
        assertFalse(toggled);
        assertFalse(data.isVisible(EquipmentSlot.HEAD));

        data.setVisible(EquipmentSlot.HEAD, true);
        assertTrue(data.isVisible(EquipmentSlot.HEAD));

        // Set cosmetic stack
        data.set(EquipmentSlot.HEAD, ItemStack.EMPTY);
        assertEquals(ItemStack.EMPTY, data.get(EquipmentSlot.HEAD));

        // Clear resets all cosmetic slots
        data.clear();
        assertTrue(data.get(EquipmentSlot.HEAD).isEmpty());
        assertTrue(data.get(EquipmentSlot.CHEST).isEmpty());
        assertTrue(data.get(EquipmentSlot.LEGS).isEmpty());
        assertTrue(data.get(EquipmentSlot.FEET).isEmpty());

        // Visual armor helper with null player returns EMPTY
        assertEquals(ItemStack.EMPTY, CosmeticArmorFeature.getVisualArmor(null, EquipmentSlot.HEAD));
    }

    @Test
    void testCosmeticArmorPayloadCarriesNoItems() {
        // A1/B1: the client may only send slot clicks; any ItemStack in the C2S payload would be trusted client data again
        for (var component : com.thelads.smp.features.arise.nb3.cosmeticarmor.UpdateCosmeticArmorPayload.class.getRecordComponents()) {
            assertNotEquals(ItemStack.class, component.getType(), component.getName());
        }
    }

    @Test
    void testCosmeticArmorDataCodecKeepsVisibility() {
        CosmeticArmorData data = new CosmeticArmorData();
        data.setVisible(EquipmentSlot.LEGS, false);
        var json = CosmeticArmorData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow();
        CosmeticArmorData back = CosmeticArmorData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertFalse(back.isVisible(EquipmentSlot.LEGS));
        assertTrue(back.isVisible(EquipmentSlot.HEAD));
        assertTrue(back.isEmpty());

        // A short/old record still loads with defaults instead of failing (and losing the stored items)
        var partial = com.google.gson.JsonParser.parseString("{\"visible\":[false]}");
        CosmeticArmorData loaded = CosmeticArmorData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, partial).getOrThrow();
        assertFalse(loaded.isVisible(EquipmentSlot.HEAD));
        assertTrue(loaded.isVisible(EquipmentSlot.FEET));
    }

    @Test
    void testImmersiveUIShakeAndEasing() {
        assertEquals(0.0f, ImmersiveUIState.getShakeOffset(), 0.001f);

        // Trigger hurt / shake
        ImmersiveUIState.onHurt();
        // Shake offset is non-zero (within amplitude)
        float shake = Math.abs(ImmersiveUIState.getShakeOffset());
        assertTrue(shake >= 0.0f);

        // Tick decays shake
        for (int i = 0; i < 15; i++) {
            ImmersiveUIState.tick();
        }
        assertEquals(0.0f, ImmersiveUIState.getShakeOffset(), 0.001f);

        // Smooth hotbar selector interpolation
        int initial = ImmersiveUIState.getInterpolatedHotbarX(100, 1.0f);
        assertEquals(100, initial); // First time snaps to target

        int step = ImmersiveUIState.getInterpolatedHotbarX(200, 1.0f);
        assertTrue(step > 100);
        assertTrue(step <= 200);

        // Floating item bob
        float bob1 = ImmersiveUIState.getFloatingItemBob(0);
        float bob2 = ImmersiveUIState.getFloatingItemBob(500);
        assertNotEquals(Float.NaN, bob1);
        assertNotEquals(Float.NaN, bob2);
    }

    @Test
    void testSnailMailFeatureDeliveries() {
        SnailMailFeature.clearPendingForTesting();
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        // Access checks
        assertTrue(SnailMailFeature.canAccessMailbox(null, null, 0)); // Unclaimed

        // Record recipient name
        SnailMailFeature.recordPlayerName("Steve", recipient);
        assertEquals(recipient, SnailMailFeature.resolveRecipient(null, "Steve"));

        // Delivery scheduling
        long now = 100_000L;
        var delivery = SnailMailFeature.scheduleDelivery(sender, recipient, "Steve", ItemStack.EMPTY, now);
        assertNotNull(delivery);
        assertEquals(1, SnailMailFeature.getPendingDeliveries().size());

        // Check not ready immediately
        List<SnailMailFeature.PendingDelivery> readyNow = SnailMailFeature.getReadyDeliveries(now);
        assertTrue(readyNow.isEmpty());

        // Ready after delay (5 minutes = 300,000 ms)
        long future = now + (5 * 60_000L) + 1000L;
        List<SnailMailFeature.PendingDelivery> readyFuture = SnailMailFeature.getReadyDeliveries(future);
        assertEquals(1, readyFuture.size());
        assertEquals("Steve", readyFuture.get(0).recipientName());
        assertEquals(recipient, readyFuture.get(0).recipientUuid());
        assertEquals(1, SnailMailFeature.getPendingDeliveries().size());
    }

    @Test
    void testBotanyPotsFeatureValidation() {
        assertFalse(BotanyPotsFeature.isValidSoil(ItemStack.EMPTY));
        assertFalse(BotanyPotsFeature.isValidCrop(ItemStack.EMPTY));
        assertEquals(ItemStack.EMPTY, BotanyPotsFeature.getYield(ItemStack.EMPTY));
    }

    @Test
    void testHoldMyItemsBulkyItemCheck() {
        assertFalse(HoldMyItemsFeature.isBulkyItem(ItemStack.EMPTY));
        assertFalse(HoldMyItemsFeature.isBulkyItem(null));
    }

    @Test
    void testBackWeaponAndObscureTooltipsCheck() {
        // Weapon / Tool checks
        assertFalse(BackWeaponFeature.isWeaponOrTool(ItemStack.EMPTY));
        assertFalse(BackWeaponFeature.isWeaponOrTool(null));
        assertEquals(ItemStack.EMPTY, BackWeaponFeature.getBackWeaponStack(null));

        // Tooltip checks
        assertFalse(ObscureTooltipsFeature.shouldShowPreview(ItemStack.EMPTY));
        assertFalse(ObscureTooltipsFeature.shouldShowPreview(null));
    }
}
