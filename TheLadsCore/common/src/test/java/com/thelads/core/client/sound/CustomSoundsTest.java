package com.thelads.core.client.sound;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.killbanner.KillBannerStyle;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.killbanner.KillDetector;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.modules.KillBannerModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uploaded sounds (CustomSounds), Combo Sounds' counting and Kill Banner's "Your sounds". */
class CustomSoundsTest {
    private static final long S = 1_000_000_000L;

    @Test
    void mp3DecodesToTheToneItHolds() throws IOException {
        // combo.mp3: an ID3-tagged 440 Hz sine, 44.1 kHz mono, 128 kbps, three MPEG frames (Windows' encoder kept 0.078 s of 0.15).
        CustomSounds.Pcm pcm;
        try (InputStream in = getClass().getResourceAsStream("/sounds/combo.mp3")) {
            pcm = CustomSounds.decode("combo.mp3", in);
        }
        assertEquals(44100, (int) pcm.format().getSampleRate());
        assertEquals(1, pcm.format().getChannels());
        assertEquals(3 * 1152, pcm.frames(), "every frame decoded");
        short[] s = samples(pcm);
        int crossings = 0, peak = 0;
        int from = (int) (.03 * 44100), to = (int) (.075 * 44100);
        for (int i = from; i < to; i++) {
            if ((s[i] >= 0) != (s[i + 1] >= 0)) crossings++;
            peak = Math.max(peak, Math.abs(s[i]));
        }
        double hz = crossings / 2.0 / ((to - from) / 44100.0);
        assertEquals(440, hz, 15, "decoded pitch");
        assertTrue(peak > 10000, "loud, not silence: " + peak);
    }

    @Test
    void wavIsConvertedTo16BitPcm() throws IOException {
        byte[] eightBit = new byte[2205]; // 0.1 s, 22.05 kHz, 8-bit unsigned mono
        for (int i = 0; i < eightBit.length; i++) eightBit[i] = (byte) (128 + (i % 20 < 10 ? 60 : -60));
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 22050, 8, 1, 1, 22050, false);
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(eightBit), format, eightBit.length), AudioFileFormat.Type.WAVE, wav);
        CustomSounds.Pcm pcm = CustomSounds.decode("tone.wav", new ByteArrayInputStream(wav.toByteArray()));
        assertEquals(22050, (int) pcm.format().getSampleRate());
        assertEquals(2205, samples(pcm).length);
        assertTrue(samples(pcm)[0] > 10000 && samples(pcm)[15] < -10000, "8-bit levels widened to 16 bits");
    }

    @Test
    void unreadableFilesAreRefused() {
        assertThrows(IOException.class, () -> CustomSounds.decode("x.mp3", new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class, () -> CustomSounds.decode("x.mp3", new ByteArrayInputStream(new byte[2048])));
        assertThrows(IOException.class, () -> CustomSounds.decode("x.wav", new ByteArrayInputStream("not a wav".getBytes())));
        // broken.mp3: 2 KB of noise, where the MP3 decoder ran off its own buffer (ArrayIndexOutOfBoundsException) before 1.9.1.
        assertThrows(IOException.class, () -> CustomSounds.decode("broken.mp3", getClass().getResourceAsStream("/sounds/broken.mp3")));
    }

    @Test
    void uploadsAreCopiedIntoTheSharedSoundsFolder(@TempDir Path base, @TempDir Path picked) throws IOException {
        ClientPaths.setBaseDir(base);
        try {
            Path source = picked.resolve("hit.mp3");
            try (InputStream in = getClass().getResourceAsStream("/sounds/combo.mp3")) { Files.copy(in, source); }
            assertEquals("hit.mp3", CustomSounds.upload(source));
            assertTrue(Files.exists(base.resolve("shared").resolve("sounds").resolve("hit.mp3")), "beside the shared config");
            assertEquals("hit.mp3", CustomSounds.upload(source), "the same file again reuses its copy");
            byte[] other = Files.readAllBytes(source);
            other[other.length - 1] ^= 1;
            Path second = Files.createDirectories(picked.resolve("b")).resolve("hit.mp3");
            Files.write(second, other);
            assertEquals("hit (2).mp3", CustomSounds.upload(second), "another file with the same name");
            Path text = Files.writeString(picked.resolve("notes.txt"), "hi");
            assertThrows(IOException.class, () -> CustomSounds.upload(text));
        } finally {
            ClientPaths.setBaseDir(null);
        }
    }

    @Test
    void trimDataIsCachedAndInvalidatedOnSave(@TempDir Path base) throws IOException {
        ClientPaths.setBaseDir(base);
        try {
            CustomSounds.saveTrimData("hit.wav", new TrimData(1.5));
            assertEquals(1.5, CustomSounds.loadTrimData("hit.wav").volume());
            // Modify the underlying file directly on disk without saveTrimData:
            // Since it's cached in memory, loadTrimData returns cached value 1.5
            Files.writeString(CustomSounds.trimDataFile("hit.wav"), "{\"volume\":0.5}");
            assertEquals(1.5, CustomSounds.loadTrimData("hit.wav").volume(), "cached value returned without re-reading file");

            // Calling saveTrimData invalidates the cache:
            CustomSounds.saveTrimData("hit.wav", new TrimData(0.8));
            assertEquals(0.8, CustomSounds.loadTrimData("hit.wav").volume(), "cache invalidated and updated");
        } finally {
            ClientPaths.setBaseDir(null);
        }
    }

    @Test
    void comboCountsLandedHitsUntilYouAreHurtOrItTimesOut() {
        ComboSoundsModule combo = new ComboSoundsModule();
        assertFalse(combo.isEnabled(), "off by default");
        combo.attacked(7, true, 0);
        assertEquals(1, combo.hurt(7, false, S / 10));
        assertEquals(0, combo.hurt(7, false, S / 5), "a hurt with no attack before it is not your hit");
        combo.attacked(7, true, S);
        assertEquals(0, combo.hurt(8, false, S + 1), "someone else hurt");
        assertEquals(2, combo.hurt(7, false, S + 2));
        combo.attacked(7, true, 2 * S);
        assertEquals(0, combo.hurt(7, false, 2 * S + 2 * S), "a hurt long after the swing is no confirmation");
        combo.attacked(7, true, 4 * S);
        assertEquals(1, combo.hurt(7, false, 4 * S + 1), "more than Combo Reset (2 s) since the last hit: a new combo");
        combo.attacked(7, true, 5 * S);
        assertEquals(2, combo.hurt(7, false, 5 * S + 1));
        combo.hurt(99, true, 5 * S + 2);
        combo.attacked(7, true, 6 * S);
        assertEquals(1, combo.hurt(7, false, 6 * S + 1), "taking damage ends the combo");
        combo.attacked(3, false, 7 * S);
        assertEquals(0, combo.hurt(3, false, 7 * S + 1), "Mobs is off by default");
        combo.mobs.set(true);
        combo.attacked(3, false, 7 * S + 2);
        assertEquals(2, combo.hurt(3, false, 7 * S + 3));

        assertSame(combo.steps[0], combo.slot(1));
        assertSame(combo.steps[4], combo.slot(9), "steps past 5 use Combo 5");
        assertTrue(ComboSoundsModule.pitch(1) < ComboSoundsModule.pitch(2) && ComboSoundsModule.pitch(30) == 2f);
    }

    @Test
    void killBannerYourSoundsKeepSavedChoicesAndFallBackToTheChime() {
        KillBannerModule module = new KillBannerModule();
        assertEquals(KillBannerStyle.values().length, module.customSound.getChoices().length, "Custom Sound's saved indexes do not move");
        module.bannerStyle.setIndex(KillBannerModule.CUSTOM);
        assertFalse(module.chosen().ownSounds());
        module.ownSounds.set(true);
        assertTrue(module.chosen().ownSounds());
        assertNull(module.chosen().soundStyle());
        module.killSounds[0].set("one.mp3");
        module.killSounds[4].set("ace.mp3");
        assertEquals("one.mp3", module.killSound(1));
        assertEquals("", module.killSound(2), "empty slot: the chime");
        assertEquals("ace.mp3", module.killSound(7), "aces use Kill 5");
        assertTrue(module.pickerOption(module.killSounds[2]) && module.pickerOption(module.ownSounds), "drawn by the picker");

        KillBanners.reset();
        String sound = KillBanners.fire(module, new KillDetector.Kill(KillDetector.Kind.PLAYER, "bob", false), 0);
        assertNull(sound, "kill 1 plays your file itself");
        sound = KillBanners.fire(module, new KillDetector.Kill(KillDetector.Kind.PLAYER, "bob", false), 1);
        assertEquals("", sound, "kill 2 has no file: the chime");
        module.ownSounds.set(false);
        module.randomize.setIndex(KillBannerModule.RANDOM_OFF);
        assertTrue(KillBanners.fire(module, new KillDetector.Kill(KillDetector.Kind.PLAYER, "bob", false), 2).startsWith("theladscore:"));
        KillBanners.reset();
    }

    private static short[] samples(CustomSounds.Pcm pcm) {
        byte[] d = pcm.data();
        short[] s = new short[d.length / 2];
        for (int i = 0; i < s.length; i++) s[i] = (short) (d[2 * i] & 0xFF | d[2 * i + 1] << 8);
        return s;
    }
}
