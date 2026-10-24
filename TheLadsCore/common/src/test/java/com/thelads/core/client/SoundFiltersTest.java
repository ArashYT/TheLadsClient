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
        SoundFilters.caveReverb = true;
        SoundFilters.muffleIntensity = 0.7f;
        SoundFilters.reverbIntensity = 0.5f;
        SoundFilters.inWater = false;
        SoundFilters.inCave = false;
    }

    @Test
    void normalSoundUnmodifiedWhenNotInWaterOrCave() {
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
    void caveReverbModulatesSound() {
        SoundFilters.inCave = true;
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient");
        assertTrue(vol > 1.0f, "Cave reverb slightly boosts ambient body");
        assertTrue(pitch <= 1.0f, "Pitch has slight resonance adjustment");
    }

    @Test
    void uiSoundsBypassFiltering() {
        SoundFilters.inWater = true;
        SoundFilters.inCave = true;
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
