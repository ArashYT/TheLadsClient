package com.thelads.core;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.SoundVolumes;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.ModuleSupport;
import org.junit.jupiter.api.Test;

/** Sound Volumes' sliders are found through the settings search ("searchable list"). */
class SoundVolumesSearchTest {
    @Test
    void searchingASoundFindsSoundVolumes() throws Exception {
        try (var ownership = new ModsMenuTest.OwnershipFixture()) {
            ModuleSupport.registerBuiltIn(SoundVolumes.NAME);
            LadsSettingsScreen screen = new LadsSettingsScreen();
            screen.setSearchQuery("thunder"); // a slider's name, not in the module's name or description
            assertTrue(screen.getFilteredModules().stream().anyMatch(m -> m.getName().equals(SoundVolumes.NAME)));
            screen.setSearchQuery("thunderstorm");
            assertFalse(screen.getFilteredModules().stream().anyMatch(m -> m.getName().equals(SoundVolumes.NAME)));
        }
    }
}
