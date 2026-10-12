package com.thelads.core.client.sound;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.SoundListOption;
import com.thelads.core.modules.ComboSoundsModule;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1.9.1 Combo Sounds: sounds after Combo 5, Sound Sets, the bow sounds' signals, and the trimmer's saved part. */
class ComboSoundsBowTest {
    private static final long MS = 1_000_000L;
    private static final int LONG = ComboSoundsModule.LONG_SHOT, HEAD = ComboSoundsModule.HEADSHOT;

    @Test
    void afterComboFiveRandomNeverRepeatsInOrderRotatesShufflePlaysEachOnce() {
        ComboSoundsModule combo = new ComboSoundsModule();
        combo.steps[4].set("five.mp3");
        assertEquals("", afterSound(combo, 6), "no list: Combo 5 again");
        combo.after.set(List.of("a.mp3", "b.mp3", "c.mp3"));
        String last = "";
        for (int hit = 6; hit < 200; hit++) {
            String file = afterSound(combo, hit);
            assertNotEquals(last, file, "Random never plays the same file twice in a row");
            last = file;
        }
        combo.afterOrder.setIndex(ComboSoundsModule.AFTER_IN_ORDER);
        List<String> order = new ArrayList<>();
        for (int hit = 6; hit <= 10; hit++) order.add(afterSound(combo, hit));
        assertEquals(List.of("a.mp3", "b.mp3", "c.mp3", "a.mp3", "b.mp3"), order, "hit 6 is the first, then down the list");
        combo.after.move(2, -1);
        assertEquals("c.mp3", afterSound(combo, 7), "the player's order");
        combo.afterOrder.setIndex(ComboSoundsModule.AFTER_SHUFFLE);
        for (int round = 0; round < 20; round++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 3; i++) assertTrue(seen.add(afterSound(combo, 6 + i)), "each once a round");
        }
    }

    private static String afterSound(ComboSoundsModule combo, int step) {
        try {
            var method = ComboSoundsModule.class.getDeclaredMethod("afterSound", int.class);
            method.setAccessible(true);
            return (String) method.invoke(combo, step);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void soundSetsSaveSwapRenameAndFollowTheConfig() {
        ComboSoundsModule combo = new ComboSoundsModule();
        assertEquals("Default pling", combo.soundSets().get(0).name());
        assertTrue(combo.using(ComboSoundsModule.DEFAULT_PLING), "a new player uses Default pling");
        for (int i = 0; i < 5; i++) combo.steps[i].set("hit" + (i + 1) + ".mp3");
        combo.after.set(List.of("x.mp3"));
        combo.afterOrder.setIndex(ComboSoundsModule.AFTER_SHUFFLE);
        int index = combo.saveSet();
        assertEquals("Set 1", combo.sets.get().get(index).name());
        combo.renameSet(index, "  Bedwars  ");
        assertEquals("Bedwars", combo.sets.get().get(0).name());
        combo.renameSet(index, "   ");
        assertEquals("Bedwars", combo.sets.get().get(0).name(), "a blank name keeps the old one");
        combo.use(ComboSoundsModule.DEFAULT_PLING);
        assertFalse(combo.steps[0].isSet());
        assertEquals(0, combo.after.size());
        ComboSoundsModule.SoundSet bedwars = combo.soundSets().get(1);
        combo.use(bedwars);
        assertEquals("hit3.mp3", combo.steps[2].get());
        assertEquals(List.of("x.mp3"), combo.after.get());
        assertEquals(ComboSoundsModule.AFTER_SHUFFLE, combo.afterOrder.getIndex());
        assertTrue(combo.using(bedwars));

        ComboSoundsModule other = new ComboSoundsModule(); // another version reading the shared config
        other.sets.load(combo.sets.save());
        other.favourites.load(combo.favourites.save());
        assertEquals(combo.sets.get(), other.sets.get());
        combo.getOptions().forEach(com.thelads.core.config.Option::reset);
        assertEquals(1, combo.sets.get().size(), "Reset options keeps the sets");

        combo.favourites.toggle("hit2.mp3");
        combo.getOptions().forEach(com.thelads.core.config.Option::reset);
        assertTrue(combo.favourites.contains("hit2.mp3"), "and the favourites");

        combo.use(bedwars);
        combo.forget("hit2.mp3");
        assertEquals("", combo.steps[1].get(), "a deleted sound leaves its slot");
        assertEquals("", combo.sets.get().get(0).steps().get(1), "and the sets");
        assertFalse(combo.favourites.contains("hit2.mp3"));
        combo.deleteSet(0);
        assertEquals(1, combo.soundSets().size(), "Default pling stays");
    }

    @Test
    void aPlayerHitIsTheArrowHitEventWithTheHurtBeforeIt() {
        ComboSoundsModule combo = new ComboSoundsModule();
        long t = 1_000 * MS;
        assertEquals(0, combo.arrowCrossed(7, true, true, 60, t), "a crossing alone is no hit");
        assertEquals(0, combo.bowHurt(7, true, 61, t + 40 * MS), "a player's hurt alone is no hit of yours");
        assertEquals(LONG | HEAD, combo.arrowHitPlayer(t + 41 * MS), "the event: 61 blocks, in the head");
        assertEquals(0, combo.arrowHitPlayer(t + 60 * MS), "each hit once");

        // The event before the client saw the arrow cross: the long shot at once, the headshot when the crossing comes.
        combo.bowHurt(8, true, 70, t + 2000 * MS);
        assertEquals(LONG, combo.arrowHitPlayer(t + 2001 * MS));
        assertEquals(HEAD, combo.arrowCrossed(8, true, true, 70, t + 2050 * MS));

        // No hurt seen: the player the last arrow crossed.
        combo.arrowCrossed(9, true, false, 55, t + 4000 * MS);
        assertEquals(LONG, combo.arrowHitPlayer(t + 4100 * MS));

        combo.bowHurt(10, true, 20, t + 6000 * MS);
        assertEquals(0, combo.arrowHitPlayer(t + 6001 * MS), "under Long Shot Distance (50), no headshot seen");
        combo.longShotDistance.setValue(15);
        combo.bowHurt(10, true, 20, t + 7000 * MS);
        assertEquals(LONG, combo.arrowHitPlayer(t + 7001 * MS), "the distance is the player's choice");
        combo.longShots.set(false);
        combo.bowHurt(10, true, 90, t + 8000 * MS);
        assertEquals(0, combo.arrowHitPlayer(t + 8001 * MS), "Long Shots off");
        assertEquals(0, combo.arrowHitPlayer(t + 9000 * MS), "no victim known: nothing");
    }

    @Test
    void aMobHitNeedsBowSoundsOnMobsAndYourArrowsCrossing() {
        ComboSoundsModule combo = new ComboSoundsModule();
        long t = 1_000 * MS;
        assertEquals(0, combo.arrowCrossed(3, false, true, 80, t));
        assertEquals(0, combo.bowHurt(3, false, 80, t + 50 * MS), "Bow Sounds on Mobs is off by default");
        combo.bowMobs.set(true);
        combo.arrowCrossed(3, false, true, 80, t + 1000 * MS);
        assertEquals(LONG | HEAD, combo.bowHurt(3, false, 80, t + 1050 * MS));
        assertEquals(0, combo.bowHurt(3, false, 80, t + 1100 * MS), "a second hurt is not a second arrow");
        assertEquals(0, combo.bowHurt(4, false, 60, t + 2000 * MS), "a hurt with no arrow of yours (a sword, someone else)");
        assertEquals(LONG, combo.arrowCrossed(4, false, false, 60, t + 2030 * MS), "unless your arrow's crossing follows at once");
        combo.bowHurt(5, false, 60, t + 3000 * MS);
        assertEquals(0, combo.arrowCrossed(5, false, true, 60, t + 4500 * MS), "too late: another shot");
        combo.arrowCrossed(6, false, true, 10, t + 6000 * MS);
        assertEquals(HEAD, combo.bowHurt(6, false, 10, t + 6010 * MS), "close: a headshot only");
    }

    @Test
    void theTrimmerSavesTheChosenPartAsAWavInTheLibrary(@TempDir Path base) throws Exception {
        ClientPaths.setBaseDir(base);
        try {
            // 20 s of 8 kHz mono: sample i holds (i / 8000) so every second has its own level.
            AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
            byte[] data = new byte[20 * 8000 * 2];
            for (int i = 0; i < data.length / 2; i++) { int v = i / 8000 * 1000; data[2 * i] = (byte) v; data[2 * i + 1] = (byte) (v >> 8); }
            CustomSounds.Pcm pcm = new CustomSounds.Pcm(format, data);
            String saved = CustomSounds.saveTrim("long song.mp3", pcm, 3, 18);
            assertEquals("long song 3.0s-15.0s.wav", saved, "at most 12 s, named after the cut");
            CustomSounds.Pcm back;
            try (InputStream in = Files.newInputStream(CustomSounds.folder().resolve(saved))) { back = CustomSounds.decode(saved, in); }
            assertEquals(12 * 8000, back.frames());
            assertEquals(3000, (short) (back.data()[0] & 0xFF | back.data()[1] << 8), "starts at second 3");
            assertEquals(saved, CustomSounds.saveTrim("long song.mp3", pcm, 3, 18), "the same cut again reuses its file");
            assertEquals(List.of(saved), CustomSounds.list());

            Path source = base.resolve("x.wav");
            Files.write(source, wav(format, data));
            CustomSounds.Pcm whole = CustomSounds.decodeForTrim(source).get();
            assertEquals(20 * 8000, whole.frames(), "the trimmer reads past the 12 s cap");
            CustomSounds.delete(saved);
            assertEquals(List.of(), CustomSounds.list());
            assertThrows(java.io.IOException.class, () -> CustomSounds.delete("../x.wav"), "only files in the sounds folder");
        } finally {
            ClientPaths.setBaseDir(null);
        }
    }

    private static byte[] wav(AudioFormat format, byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(data), format, data.length / 2),
            javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    @Test
    void soundListsMoveToggleAndSave() {
        SoundListOption list = new SoundListOption("L");
        list.set(List.of("a", "b", "c"));
        list.move(0, 1);
        assertEquals(List.of("b", "a", "c"), list.get());
        list.move(0, -1);
        assertEquals(List.of("b", "a", "c"), list.get(), "the first cannot go up");
        list.toggle("a");
        list.toggle("z");
        SoundListOption copy = new SoundListOption("L");
        copy.load(list.save());
        assertEquals(List.of("b", "c", "z"), copy.get());
    }
}
