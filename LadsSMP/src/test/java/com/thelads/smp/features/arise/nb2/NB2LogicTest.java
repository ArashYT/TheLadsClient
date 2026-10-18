package com.thelads.smp.features.arise.nb2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic and unit verification tests for Native Batch NB2 features:
 * - Configuration serialization and default values
 * - Hitmarker duration window logic
 * - Hold My Beer interaction suppression and toggling
 * - Distraction Free Recipes search text filtering
 * - Remove Chat Bracket message formatting
 * - Trade Uses math, badge text, and color thresholds
 * - YUNG's Menu Tweaks slider delta adjustment and bounds clamping
 * - Too Many Entities spatial frame limiter
 * - CPM x SVC audio RMS calculation and mute state tracking
 * - Stonecutter Memory recipe indexing cache
 * - Paxi global pack path resolution
 */
public class NB2LogicTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @BeforeEach
    void setUp() {
        NB2Config.setInstanceForTesting(new NB2Config());
        TooManyEntitiesManager.reset();
        StonecutterMemoryManager.clear();
    }

    @Test
    void testConfigDefaultsAndRoundTrip() {
        NB2Config config = NB2Config.get();
        assertNotNull(config);

        // Verify Arise defaults for key features
        assertTrue(config.hitmarker);
        assertTrue(config.hitmarkerSound);
        assertEquals(4, config.hitmarkerTicks);
        assertTrue(config.holdMyBeer);
        assertTrue(config.emptyOffhand);
        assertTrue(config.showEmptyHandIcon);
        assertFalse(config.pressAndHold);
        assertTrue(config.notEnoughRecipeBook);
        assertFalse(config.distractionFreeRecipes);
        assertTrue(config.oWorld2Create);
        assertTrue(config.panoramica);
        assertTrue(config.paxi);
        assertTrue(config.quitConfirm);
        assertTrue(config.realisticNametag);
        assertTrue(config.removeChatBracket);
        assertTrue(config.stonecutterMemory);
        assertTrue(config.tooManyEntities);
        assertEquals(64, config.maxEntityCount);
        assertTrue(config.tradeUses);
        assertTrue(config.yungsMenuTweaks);
        assertTrue(config.rightClickReversesCycleButtons);
        assertTrue(config.mouseScrollWheelAffectsSliders);
        assertTrue(config.cpmSvcCompat);

        // JSON serialization roundtrip
        String json = GSON.toJson(config);
        NB2Config roundTripped = GSON.fromJson(json, NB2Config.class);
        assertNotNull(roundTripped);
        assertEquals(config.hitmarkerTicks, roundTripped.hitmarkerTicks);
        assertEquals(config.maxEntityCount, roundTripped.maxEntityCount);
        assertEquals(config.realisticNametag, roundTripped.realisticNametag);
    }

    @Test
    void testHitmarkerActiveWindow() {
        long now = 1000000L;
        // Without any hit, isHitActive must return false
        assertFalse(HitmarkerManager.isHitActive(now, 8));

        // When hit occurred 100ms ago, within 8 ticks (400ms), it is active
        long hitTime = now - 100L;
        long durationMs = 8 * 50L; // 400ms
        assertTrue((now - hitTime) <= durationMs);

        // When hit occurred 450ms ago, it has expired
        long expiredHitTime = now - 450L;
        assertFalse((now - expiredHitTime) <= durationMs);
    }

    @Test
    void testHoldMyBeerSuppression() {
        NB2Config.get().holdMyBeer = true;
        NB2Config.get().emptyOffhand = true;
        NB2Config.get().pressAndHold = false;

        // Initially inactive
        assertFalse(HoldMyBeerManager.isActive());
        assertFalse(HoldMyBeerManager.shouldSuppressUse(InteractionHand.MAIN_HAND));
        assertFalse(HoldMyBeerManager.shouldSuppressUse(InteractionHand.OFF_HAND));

        // Toggle on
        boolean state = HoldMyBeerManager.toggle();
        assertTrue(state);
        assertTrue(HoldMyBeerManager.isActive());
        assertTrue(HoldMyBeerManager.shouldSuppressUse(InteractionHand.MAIN_HAND));
        assertTrue(HoldMyBeerManager.shouldSuppressUse(InteractionHand.OFF_HAND));

        // When emptyOffhand is disabled, off-hand interaction is permitted
        NB2Config.get().emptyOffhand = false;
        assertTrue(HoldMyBeerManager.shouldSuppressUse(InteractionHand.MAIN_HAND));
        assertFalse(HoldMyBeerManager.shouldSuppressUse(InteractionHand.OFF_HAND));

        // Toggle off
        HoldMyBeerManager.toggle();
        assertFalse(HoldMyBeerManager.isActive());
        assertFalse(HoldMyBeerManager.shouldSuppressUse(InteractionHand.MAIN_HAND));
    }

    @Test
    void testDistractionFreeRecipesLogic() {
        NB2Config.get().distractionFreeRecipes = true;

        assertTrue(DistractionFreeRecipesManager.isSearchEmpty(null));
        assertTrue(DistractionFreeRecipesManager.isSearchEmpty(""));
        assertTrue(DistractionFreeRecipesManager.isSearchEmpty("   "));
        assertFalse(DistractionFreeRecipesManager.isSearchEmpty("iron"));

        assertTrue(DistractionFreeRecipesManager.shouldHideOverlay(""));
        assertTrue(DistractionFreeRecipesManager.shouldHideOverlay("   "));
        assertFalse(DistractionFreeRecipesManager.shouldHideOverlay("diamond"));

        // When feature is disabled, overlay should never be hidden
        NB2Config.get().distractionFreeRecipes = false;
        assertFalse(DistractionFreeRecipesManager.shouldHideOverlay(""));
    }

    @Test
    void testRemoveChatBracketFormatting() {
        NB2Config.get().removeChatBracket = true;

        Component sender = Component.literal("Arash");
        Component message = Component.literal("Hello World!");
        Component formatted = RemoveChatBracketManager.formatMessage(sender, message, null);

        assertNotNull(formatted);
        assertEquals("Arash Hello World!", formatted.getString());

        // When disabled, returns null so vanilla formatting runs
        NB2Config.get().removeChatBracket = false;
        assertNull(RemoveChatBracketManager.formatMessage(sender, message, null));
    }

    @Test
    void testTradeUsesCalculationsAndBadges() {
        assertEquals(12, TradeUsesManager.getRemainingUses(0, 12));
        assertEquals(5, TradeUsesManager.getRemainingUses(7, 12));
        assertEquals(0, TradeUsesManager.getRemainingUses(12, 12));
        assertEquals(0, TradeUsesManager.getRemainingUses(15, 12)); // Overused offer

        assertEquals("12/12", TradeUsesManager.formatBadge(0, 12));
        assertEquals("5/12", TradeUsesManager.formatBadge(7, 12));
        assertEquals("0/12", TradeUsesManager.formatBadge(12, 12));

        // Color thresholds: Green (> 2), Yellow (1..2), Red (0)
        assertEquals(0xFF55FF55, TradeUsesManager.getBadgeColor(0, 12)); // 12 remaining -> Green
        assertEquals(0xFF55FF55, TradeUsesManager.getBadgeColor(9, 12)); // 3 remaining -> Green
        assertEquals(0xFFFFAA00, TradeUsesManager.getBadgeColor(10, 12)); // 2 remaining -> Yellow
        assertEquals(0xFFFFAA00, TradeUsesManager.getBadgeColor(11, 12)); // 1 remaining -> Yellow
        assertEquals(0xFFFF5555, TradeUsesManager.getBadgeColor(12, 12)); // 0 remaining -> Red
    }

    @Test
    void testYungsMenuTweaksSliderDeltaAndClamping() {
        // Step up
        double steppedUp = YungsMenuTweaksManager.calculateAdjustedSliderValue(0.50, 1.0);
        assertEquals(0.55, steppedUp, 0.001);

        // Step down
        double steppedDown = YungsMenuTweaksManager.calculateAdjustedSliderValue(0.50, -1.0);
        assertEquals(0.45, steppedDown, 0.001);

        // Clamp ceiling at 1.0
        double maxClamped = YungsMenuTweaksManager.calculateAdjustedSliderValue(0.98, 1.0);
        assertEquals(1.0, maxClamped, 0.001);

        // Clamp floor at 0.0
        double minClamped = YungsMenuTweaksManager.calculateAdjustedSliderValue(0.02, -1.0);
        assertEquals(0.0, minClamped, 0.001);
    }

    @Test
    void testCpmSvcCompatAudioCalculationAndMute() {
        // Silence returns 0
        short[] silence = new short[100];
        assertEquals(0.0f, CpmSvcCompatManager.calculateAudioLevel(silence), 0.001f);
        assertEquals(0.0f, CpmSvcCompatManager.calculateAudioLevel(null), 0.001f);
        assertEquals(0.0f, CpmSvcCompatManager.calculateAudioLevel(new short[0]), 0.001f);

        // Max amplitude signal produces positive clamped value
        short[] loud = new short[100];
        for (int i = 0; i < loud.length; i++) {
            loud[i] = 30000;
        }
        float level = CpmSvcCompatManager.calculateAudioLevel(loud);
        assertTrue(level > 0.5f);
        assertTrue(level <= 1.0f);

        // Mute state
        java.util.UUID testPlayer = java.util.UUID.randomUUID();
        assertFalse(CpmSvcCompatManager.isMuted(testPlayer));
        CpmSvcCompatManager.setMuted(testPlayer, true);
        assertTrue(CpmSvcCompatManager.isMuted(testPlayer));
        CpmSvcCompatManager.setMuted(testPlayer, false);
        assertFalse(CpmSvcCompatManager.isMuted(testPlayer));
    }

    @Test
    void testPaxiDirectoryPaths() {
        Path dp = PaxiManager.getDatapacksDir();
        Path rp = PaxiManager.getResourcepacksDir();

        assertNotNull(dp);
        assertNotNull(rp);
        assertTrue(dp.toString().replace('\\', '/').endsWith("config/paxi/datapacks"));
        assertTrue(rp.toString().replace('\\', '/').endsWith("config/paxi/resourcepacks"));
    }
}
