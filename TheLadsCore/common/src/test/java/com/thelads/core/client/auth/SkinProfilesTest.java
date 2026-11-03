package com.thelads.core.client.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SkinProfilesTest {
    private static final UUID REAL = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private static <P> P await(SkinProfiles<P> profiles, UUID id) throws InterruptedException {
        for (int i = 0; i < 400; i++) {
            P profile = profiles.get(id);
            if (profile != null) return profile;
            Thread.sleep(5);
        }
        return null;
    }

    @Test
    public void getNeverWaitsAndAsksOncePerId() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        SkinProfiles<String> profiles = new SkinProfiles<>(id -> {
            calls.incrementAndGet();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            return "textures of " + id;
        });

        long start = System.nanoTime();
        for (int frame = 0; frame < 100; frame++) assertNull(profiles.get(REAL), "the slow lookup must not hold up a frame");
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);

        release.countDown();
        assertEquals("textures of " + REAL, await(profiles, REAL));
        for (int frame = 0; frame < 100; frame++) assertNotNull(profiles.get(REAL));
        assertEquals(1, calls.get());
    }

    @Test
    public void anOfflineIdIsNeverLookedUp() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SkinProfiles<String> profiles = new SkinProfiles<>(id -> { calls.incrementAndGet(); return "x"; });
        assertNull(profiles.get(LadsAccount.offlineUuid("Notch")));
        assertNull(profiles.get(new UUID(0L, 0L)));
        Thread.sleep(50);
        assertEquals(0, calls.get());
    }

    @Test
    public void aFailedLookupIsHarmlessAndNotRepeatedEveryFrame() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SkinProfiles<String> profiles = new SkinProfiles<>(id -> { calls.incrementAndGet(); throw new IllegalStateException("no connection"); });
        for (int frame = 0; frame < 50; frame++) { assertNull(profiles.get(REAL)); Thread.sleep(2); }
        assertEquals(1, calls.get());
    }

    @Test
    public void aHeadWithoutAnIdIsTheGamesOwnWhenTheNameIs() {
        UUID self = UUID.randomUUID();
        assertEquals(self.toString(), SkinProfiles.orSession("arash_yt", null, "Arash_YT", self));
        assertEquals(self.toString(), SkinProfiles.orSession("Arash_YT", " ", "Arash_YT", self));
        assertNull(SkinProfiles.orSession("Steve", null, "Arash_YT", self));
        assertEquals("given", SkinProfiles.orSession("Arash_YT", "given", "Arash_YT", self));
    }
}
