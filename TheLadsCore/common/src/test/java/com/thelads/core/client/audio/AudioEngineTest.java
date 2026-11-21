package com.thelads.core.client.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioEngineTest {
    private static SourcePool.Candidate c(String k, int p, double d, int t, boolean loop) {
        return new SourcePool.Candidate(k, p, d, t, loop, false);
    }

    @Test
    void priorityClassification() {
        assertEquals(SoundPriority.MUSIC, SoundPriority.classify("MUSIC", "music.menu", 0));
        assertEquals(SoundPriority.MUSIC, SoundPriority.classify("RECORDS", "music_disc.cat", 5));
        assertEquals(SoundPriority.CRITICAL, SoundPriority.classify("PLAYERS", "entity.player.hurt", 1));
        assertEquals(SoundPriority.CRITICAL, SoundPriority.classify("HOSTILE", "entity.zombie.damage", 3));
        assertEquals(SoundPriority.HOSTILE, SoundPriority.classify("HOSTILE", "entity.zombie.ambient", 10));
        assertEquals(SoundPriority.AMBIENT, SoundPriority.classify("WEATHER", "weather.rain", 10));
        assertEquals(SoundPriority.NEUTRAL, SoundPriority.classify("BLOCKS", "block.stone.step", 3));
    }

    @Test
    void parseUsage() {
        assertArrayEquals(new int[]{12, 247, 0, 8}, SourcePool.parseUsage("Sounds: 12/247 + 0/8"));
        assertNull(SourcePool.parseUsage("garbage"));
        assertNull(SourcePool.parseUsage(null));
        assertTrue(SourcePool.nearLimit(223, 247));
        assertFalse(SourcePool.nearLimit(100, 247));
    }

    @Test
    void victimIsLowestPriorityFarthestOldest() {
        List<SourcePool.Candidate> l = new ArrayList<>();
        l.add(c("music", SoundPriority.MUSIC, 999, 0, false));
        l.add(c("loop", SoundPriority.AMBIENT, 999, 0, true));
        l.add(c("ambNear", SoundPriority.AMBIENT, 4, 5, false));
        l.add(c("ambFarOld", SoundPriority.AMBIENT, 400, 1, false));
        l.add(c("ambFarNew", SoundPriority.AMBIENT, 400, 9, false));
        l.add(c("host", SoundPriority.HOSTILE, 900, 1, false));
        assertEquals("ambFarOld", SourcePool.pickVictim(l, SoundPriority.HOSTILE, 25, false).key);
    }

    @Test
    void noVictimWhenNothingLowerOrMuchFarther() {
        List<SourcePool.Candidate> l = new ArrayList<>();
        l.add(c("a", SoundPriority.HOSTILE, 30, 0, false));
        assertNull(SourcePool.pickVictim(l, SoundPriority.HOSTILE, 25, false));
        assertNotNull(SourcePool.pickVictim(l, SoundPriority.HOSTILE, 5, false));
        assertNull(SourcePool.pickVictim(l, SoundPriority.AMBIENT, 1, false));
    }

    @Test
    void capVictim() {
        List<SourcePool.Candidate> l = new ArrayList<>();
        l.add(c("far", SoundPriority.AMBIENT, 100, 0, false));
        l.add(c("near", SoundPriority.AMBIENT, 4, 0, false));
        assertEquals("far", SourcePool.pickCapVictim(l, SoundPriority.AMBIENT, 9).key);
        assertNull(SourcePool.pickCapVictim(l, SoundPriority.AMBIENT, 400));
    }

    @Test
    void dedupFirstNPlayRestDropped() {
        SoundDedup d = new SoundDedup();
        int plays = 0, drops = 0, boosted = 0;
        for (int i = 0; i < 12; i++) {
            int r = d.onSound(10, "minecraft:entity.arrow.hit", 1 + i * 0.1, 64, 1, 5, 8);
            if (r == SoundDedup.DROP) drops++; else { plays++; if (r > 1) { boosted++; assertEquals(5, r); } }
        }
        assertEquals(5, plays);
        assertEquals(7, drops);
        assertEquals(1, boosted);
    }

    @Test
    void dedupResetsNextTickAndSeparatesFarAndOtherIds() {
        SoundDedup d = new SoundDedup();
        for (int i = 0; i < 8; i++) d.onSound(1, "a", 0, 0, 0, 3, 8);
        assertEquals(SoundDedup.DROP, d.onSound(1, "a", 0, 0, 0, 3, 8));
        assertEquals(SoundDedup.PLAY, d.onSound(1, "a", 50, 0, 0, 3, 8));
        assertEquals(SoundDedup.PLAY, d.onSound(1, "b", 0, 0, 0, 3, 8));
        assertEquals(SoundDedup.PLAY, d.onSound(2, "a", 0, 0, 0, 3, 8));
    }

    @Test
    void boostIsLog() {
        assertEquals(1f, SoundDedup.boost(1), 1e-6);
        assertEquals(2f, SoundDedup.boost(10), 1e-5);
        assertEquals(1.69897f, SoundDedup.boost(5), 1e-4);
    }

    @Test
    void neverDedupInteractionAndUi() {
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.wooden_door.open", true, false));
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.fence_gate.close", true, false));
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.stone.break", true, false));
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.stone.place", true, false));
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.stone.hit", true, false));
        assertFalse(SoundDedup.dedupable("BLOCKS", "block.wood.step", true, false));
        assertFalse(SoundDedup.dedupable("MASTER", "ui.button.click", false, false));
        assertFalse(SoundDedup.dedupable("MUSIC", "music.game", true, false));
        assertFalse(SoundDedup.dedupable("RECORDS", "music_disc.13", true, false));
        assertFalse(SoundDedup.dedupable("AMBIENT", "ambient.cave", true, true));
        assertTrue(SoundDedup.dedupable("NEUTRAL", "entity.arrow.hit", true, false));
        assertTrue(SoundDedup.dedupable("HOSTILE", "entity.zombie.ambient", true, false));
    }

    @Test
    void lodCutoffMatchesLinearAttenuation() {
        assertEquals(1 + 15 * 0.98, AudioLod.cutoff(1.0, 16), 1e-9);
        assertTrue(AudioLod.cull(16, 1.0, 16, 1.0));
        assertFalse(AudioLod.cull(10, 1.0, 16, 1.0));
        assertFalse(AudioLod.cull(15, 1.0, 16, 2.0));
        assertEquals(Double.POSITIVE_INFINITY, AudioLod.cutoff(0.01, 16));
        assertFalse(AudioLod.cull(1000, 0.01, 16, 1.0));
        assertTrue(AudioLod.cutoff(0.5, 16) < AudioLod.cutoff(1.0, 16));
    }

    @Test
    void blacklist() {
        assertFalse(SoundBlacklist.matches("", "minecraft:block.stone.step"));
        String pats = "minecraft:entity\\.bat\\..*, block\\.grass";
        assertTrue(SoundBlacklist.matches(pats, "minecraft:entity.bat.ambient"));
        assertTrue(SoundBlacklist.matches(pats, "minecraft:block.grass.step"));
        assertFalse(SoundBlacklist.matches(pats, "minecraft:block.stone.step"));
        assertFalse(SoundBlacklist.matches("([unclosed, ", "minecraft:x"));
        assertTrue(SoundBlacklist.matches("([unclosed, x", "minecraft:x"));
    }

    @Test
    void occlusionGain() {
        assertEquals(1f, Occlusion.gainFor(0), 1e-6);
        assertEquals(0.75f, Occlusion.gainFor(1), 1e-6);
        assertEquals((float) Math.pow(0.75, 4), Occlusion.gainFor(9), 1e-6);
    }

    @Test
    void occlusionBudgetRoundRobinAndCache() {
        Occlusion<Object> o = new Occlusion<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 6; i++) keys.add(new Object());
        int[] calls = {0};
        Occlusion.Probe<Object> probe = (k, rays) -> { calls[0]++; rays[0] += 2; return 1; };
        assertEquals(4, o.update(100, 5, 4, keys, probe));
        assertEquals(2, calls[0]);
        assertEquals(4, o.update(101, 5, 4, keys, probe));
        assertEquals(4, calls[0]);
        o.update(102, 5, 4, keys, probe);
        assertEquals(6, calls[0]);
        assertEquals(0.75f, o.gain(keys.get(0)), 1e-6);
        assertEquals(0, o.update(103, 5, 100, keys, probe));
        assertEquals(6, calls[0]);
        assertTrue(o.update(110, 5, 100, keys, probe) > 0);
    }

    @Test
    void occlusionFirstExactThenEases() {
        Occlusion<Object> o = new Occlusion<>();
        Object k = new Object();
        List<Object> l = new ArrayList<>();
        l.add(k);
        o.update(0, 1, 10, l, (key, r) -> { r[0]++; return 2; });
        assertEquals(0.5625f, o.gain(k), 1e-6);
        o.update(1, 1, 10, l, (key, r) -> { r[0]++; return 0; });
        float g = o.gain(k);
        assertTrue(g > 0.5625f && g < 1f);
    }
}
