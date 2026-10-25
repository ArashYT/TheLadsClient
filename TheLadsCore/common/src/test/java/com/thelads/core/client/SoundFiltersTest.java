package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoundFiltersTest {

    @BeforeEach
    void setUp() {
        SoundFilters.on = true;
        SoundFilters.underwaterMuffle = true;
        SoundFilters.muffleIntensity = 0.7f;
        SoundFilters.inWater = false;
    }

    @Test
    void normalSoundUnmodifiedWhenNotInWater() {
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient");
        assertEquals(1.0f, vol, 1e-5);
        assertEquals(1.0f, pitch, 1e-5);
    }

    @Test
    void underwaterMufflingReducesVolumeAndPitch() {
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient");
        assertTrue(vol < 1.0f, "Volume should be muffled underwater");
        assertTrue(pitch < 1.0f, "Pitch should be lowered underwater");
    }

    @Test
    void musicAndRecordsExemptFromPitchShift() {
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "music.game");
        float pitch = SoundFilters.filterPitch(1.0f, "music.game");
        assertEquals(1.0f, pitch, 1e-5, "Music pitch must remain unchanged underwater");

        float recordPitch = SoundFilters.filterPitch(1.0f, "record.cat");
        assertEquals(1.0f, recordPitch, 1e-5, "Record pitch must remain unchanged underwater");
    }

    @Test
    void uiSoundsBypassFiltering() {
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "gui.button.press");
        float pitch = SoundFilters.filterPitch(1.0f, "gui.button.press");
        assertEquals(1.0f, vol, 1e-5, "UI button sound must not be muffled");
        assertEquals(1.0f, pitch, 1e-5, "UI button sound must keep original pitch");
    }

    @Test
    void moduleDisabledBypassesFiltering() {
        SoundFilters.on = false;
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient");
        assertEquals(1.0f, vol, 1e-5);
        assertEquals(1.0f, pitch, 1e-5);
    }
}
