package com.thelads.core.client.pack;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackProfileManagerTest {
    @BeforeEach
    void setUp() {
        PackProfileManager.clear();
    }

    @Test
    void testSaveAndGetProfile() {
        PackProfileManager.saveProfile("PvP", List.of("pack1.zip", "pack2.zip"));
        assertEquals(List.of("pack1.zip", "pack2.zip"), PackProfileManager.getProfile("PvP"));
        assertTrue(PackProfileManager.getProfileNames().contains("PvP"));
    }

    @Test
    void testSerializationRoundTrip() {
        PackProfileManager.saveProfile("PvP", List.of("p1", "p2"));
        PackProfileManager.saveProfile("Default", List.of("vanilla"));
        String serialized = PackProfileManager.serialize();

        PackProfileManager.clear();
        assertTrue(PackProfileManager.getProfileNames().isEmpty());

        PackProfileManager.deserialize(serialized);
        assertEquals(List.of("p1", "p2"), PackProfileManager.getProfile("PvP"));
        assertEquals(List.of("vanilla"), PackProfileManager.getProfile("Default"));
    }

    @Test
    void testDeleteProfile() {
        PackProfileManager.saveProfile("Temp", List.of("pack.zip"));
        PackProfileManager.setActiveProfile("Temp");
        assertEquals("Temp", PackProfileManager.getActiveProfile());

        assertTrue(PackProfileManager.deleteProfile("Temp"));
        assertEquals("", PackProfileManager.getActiveProfile());
        assertTrue(PackProfileManager.getProfile("Temp").isEmpty());
    }
}
