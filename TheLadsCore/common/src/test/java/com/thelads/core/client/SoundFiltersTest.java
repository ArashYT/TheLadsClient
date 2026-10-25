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
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient", "HOSTILE");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient", "HOSTILE");
        assertEquals(1.0f, vol, 1e-5);
        assertEquals(1.0f, pitch, 1e-5);
    }

    @Test
    void underwaterMufflingReducesVolumeAndPitch() {
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient", "HOSTILE");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient", "HOSTILE");
        assertTrue(vol < 1.0f, "Volume should be muffled underwater");
        assertTrue(pitch < 1.0f, "Pitch should be lowered underwater");
    }

    @Test
    void musicAndRecordsExemptByCategory() {
        SoundFilters.inWater = true;
        // QA 1.11.0 R2-F4: volume too, and by category: the id says nothing (a pack's music, a mod's disc, 1.8.9 "records.cat")
        for (String[] sound : new String[][] {{"music.game", "MUSIC"}, {"records.cat", "RECORDS"}, {"music_disc.cat", "RECORDS"},
                {"my_pack.boss_theme", "MUSIC"}}) {
            assertEquals(1.0f, SoundFilters.filterVolume(1.0f, sound[0], sound[1]), 1e-5, sound[0] + " volume must stay unchanged underwater");
            assertEquals(1.0f, SoundFilters.filterPitch(1.0f, sound[0], sound[1]), 1e-5, sound[0] + " pitch must stay unchanged underwater");
        }
        assertTrue(SoundFilters.filterVolume(1.0f, "music_box.click", "BLOCKS") < 1.0f, "a world sound named like music is still muffled");
    }

    @Test
    void uiSoundsBypassFiltering() {
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "gui.button.press", "MASTER");
        float pitch = SoundFilters.filterPitch(1.0f, "gui.button.press", "MASTER");
        assertEquals(1.0f, vol, 1e-5, "UI button sound must not be muffled");
        assertEquals(1.0f, pitch, 1e-5, "UI button sound must keep original pitch");
    }

    @Test
    void moduleDisabledBypassesFiltering() {
        SoundFilters.on = false;
        SoundFilters.inWater = true;
        float vol = SoundFilters.filterVolume(1.0f, "entity.zombie.ambient", "HOSTILE");
        float pitch = SoundFilters.filterPitch(1.0f, "entity.zombie.ambient", "HOSTILE");
        assertEquals(1.0f, vol, 1e-5);
        assertEquals(1.0f, pitch, 1e-5);
    }
}
