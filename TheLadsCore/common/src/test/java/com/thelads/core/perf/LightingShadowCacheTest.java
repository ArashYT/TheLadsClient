package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LightingShadowCacheTest {
    @BeforeEach
    void setUp() {
        LightingShadowCache.setDisabled(false);
        LightingShadowCache.clear();
    }

    @Test
    void cachesPackedLight() {
        assertEquals(-1, LightingShadowCache.getLight(123456789L));
        LightingShadowCache.putLight(123456789L, 0x00F000F0);
        assertEquals(0x00F000F0, LightingShadowCache.getLight(123456789L));
    }

    @Test
    void cachesShadowGroundY() {
        assertTrue(Float.isNaN(LightingShadowCache.getShadow(987654321L)));
        LightingShadowCache.putShadow(987654321L, 64.0f);
        assertEquals(64.0f, LightingShadowCache.getShadow(987654321L), 0.001f);
    }

    @Test
    void clearFlushesAllEntries() {
        LightingShadowCache.putLight(100L, 15);
        LightingShadowCache.putShadow(200L, 70.0f);
        LightingShadowCache.clear();
        assertEquals(-1, LightingShadowCache.getLight(100L));
        assertTrue(Float.isNaN(LightingShadowCache.getShadow(200L)));
    }
}
