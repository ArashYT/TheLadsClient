package com.thelads.core.client.hypixel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyBlockTimersTest {

    @BeforeEach
    @AfterEach
    void resetTimers() {
        SkyBlockTimers.clearTimers();
    }

    @Test
    void testDarkAuctionPreview() {
        assertEquals("Dark Auction: 42m 15s", SkyBlockTimers.getDarkAuctionString(true));
    }

    @Test
    void testDarkAuctionCalculation() {
        // Epoch 0 is 1970-01-01 00:00:00 UTC (0 seconds into the hour)
        // Next DA is at 55:00 (3300 seconds)
        assertEquals(3300L, SkyBlockTimers.getDarkAuctionSecondsUntil(0L));
        assertEquals("Dark Auction: 55m 00s", SkyBlockTimers.formatDarkAuction(0L));

        // 30 minutes into the hour (1800 seconds) -> 25 minutes (1500 seconds) remaining
        assertEquals(1500L, SkyBlockTimers.getDarkAuctionSecondsUntil(1800_000L));
        assertEquals("Dark Auction: 25m 00s", SkyBlockTimers.formatDarkAuction(1800_000L));

        // 54 minutes 30 seconds (3270 seconds) -> 30 seconds remaining
        assertEquals(30L, SkyBlockTimers.getDarkAuctionSecondsUntil(3270_000L));
        assertEquals("Dark Auction: 0m 30s", SkyBlockTimers.formatDarkAuction(3270_000L));

        // Exactly at 55:00 (3300 seconds) -> Active (0s until start)
        assertEquals(0L, SkyBlockTimers.getDarkAuctionSecondsUntil(3300_000L));
        assertEquals("Dark Auction: ACTIVE (5m 00s)", SkyBlockTimers.formatDarkAuction(3300_000L));

        // At 58:15 (3495 seconds) -> Active (1m 45s remaining)
        assertEquals(0L, SkyBlockTimers.getDarkAuctionSecondsUntil(3495_000L));
        assertEquals("Dark Auction: ACTIVE (1m 45s)", SkyBlockTimers.formatDarkAuction(3495_000L));

        // At 59:59 (3599 seconds) -> Active (0m 01s remaining)
        assertEquals(0L, SkyBlockTimers.getDarkAuctionSecondsUntil(3599_000L));
        assertEquals("Dark Auction: ACTIVE (0m 01s)", SkyBlockTimers.formatDarkAuction(3599_000L));

        // At 01:00:00 (3600 seconds) -> Cycles back to 55m 00s until next DA
        assertEquals(3300L, SkyBlockTimers.getDarkAuctionSecondsUntil(3600_000L));
        assertEquals("Dark Auction: 55m 00s", SkyBlockTimers.formatDarkAuction(3600_000L));
    }

    @Test
    void testCachedStrings() {
        String s1 = SkyBlockTimers.getDarkAuctionString(false);
        String s2 = SkyBlockTimers.getDarkAuctionString(false);
        assertSame(s1, s2, "Repeated calls within same second should return identical cached string reference");
    }
}
