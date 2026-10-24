package com.thelads.core.client.mumble;

import com.thelads.core.modules.MumbleLinkModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MumbleLinkTest {
    @Test
    void testDefaults() {
        MumbleLinkModule mod = new MumbleLinkModule();
        assertFalse(mod.isEnabled());
        assertTrue(mod.link3dAudio.get());
        assertFalse(mod.showStatus.get());
    }

    @Test
    void testComputeLookVector() {
        // Yaw = 0 (facing south in MC, z positive), Pitch = 0
        var data = MumbleLinkService.compute(10.0, 64.0, -20.0, 0.0f, 0.0f, "uuid-123", "mc.hypixel.net:25565");
        assertEquals(10.0f, data.posX(), 0.001f);
        assertEquals(64.0f, data.posY(), 0.001f);
        assertEquals(-20.0f, data.posZ(), 0.001f);
        assertEquals(0.0f, data.frontX(), 0.001f);
        assertEquals(0.0f, data.frontY(), 0.001f);
        assertEquals(1.0f, data.frontZ(), 0.001f);
        assertEquals("uuid-123", data.identity());
        assertEquals("mc.hypixel.net:25565", data.context());
    }
}
