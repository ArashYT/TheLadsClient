package com.thelads.core.client.audio;

import java.util.List;

/**
 * Smart source pool policy: which playing sound to stop so a new one can start when OpenAL is out of sources.
 * Pure maths; the version code builds the candidate list from the engine's playing channels.
 */
public final class SourcePool {
    /** A sound at equal priority is only evicted for a new one when it is at least this many times farther (squared distance). */
    public static final double MUCH_FARTHER_SQ = 4.0;
    public static final double NEAR_LIMIT = 0.9;

    private SourcePool() {}

    public static final class Candidate {
        public final Object key;
        public final int priority;
        public final double distSq;
        public final int startTick;
        public final boolean looping;
        public final boolean stream;

        public Candidate(Object key, int priority, double distSq, int startTick, boolean looping, boolean stream) {
            this.key = key; this.priority = priority; this.distSq = distSq; this.startTick = startTick;
            this.looping = looping; this.stream = stream;
        }
    }

    /** Parses the engine's "Sounds: used/max + used/max" line into {staticUsed, staticMax, streamUsed, streamMax}; null if unreadable. */
    public static int[] parseUsage(String s) {
        if (s == null) return null;
        int i = s.indexOf(':');
        if (i < 0) return null;
        String[] nums = s.substring(i + 1).split("[^0-9]+");
        int[] out = new int[4];
        int n = 0;
        for (String t : nums) {
            if (t.isEmpty()) continue;
            if (n >= 4) break;
            try { out[n++] = Integer.parseInt(t); } catch (NumberFormatException e) { return null; }
        }
        return n == 4 ? out : null;
    }

    public static boolean nearLimit(int used, int max) {
        return max > 0 && used >= Math.ceil(max * NEAR_LIMIT);
    }

    /**
     * The sound to stop for a new one of {@code newPriority} at {@code newDistSq}: lowest priority first, then farthest, then oldest;
     * never a looping, music or other-pool sound. Null when nothing qualifies (the new sound is then dropped like vanilla does).
     */
    public static Candidate pickVictim(List<Candidate> list, int newPriority, double newDistSq, boolean stream) {
        Candidate best = null;
        for (Candidate c : list) {
            if (c.looping || c.priority == SoundPriority.MUSIC || c.stream != stream) continue;
            boolean ok = c.priority < newPriority || (c.priority == newPriority && c.distSq > newDistSq * MUCH_FARTHER_SQ);
            if (ok && (best == null || worse(c, best))) best = c;
        }
        return best;
    }

    /** True when a is a better eviction choice than b: lower priority, then farther, then older. */
    private static boolean worse(Candidate a, Candidate b) {
        if (a.priority != b.priority) return a.priority < b.priority;
        if (a.distSq != b.distSq) return a.distSq > b.distSq;
        return a.startTick < b.startTick;
    }

    public static int count(List<Candidate> list, int priority) {
        int n = 0;
        for (Candidate c : list) if (c.priority == priority) n++;
        return n;
    }

    /**
     * A category sits at its soft cap: the farthest (then oldest) non-looping sound of that priority is stopped when it is farther
     * than the new one; null means the new sound is the farthest and is dropped instead.
     */
    public static Candidate pickCapVictim(List<Candidate> list, int priority, double newDistSq) {
        Candidate best = null;
        for (Candidate c : list) {
            if (c.looping || c.priority != priority) continue;
            if (best == null || c.distSq > best.distSq || (c.distSq == best.distSq && c.startTick < best.startTick)) best = c;
        }
        return best != null && best.distSq > newDistSq ? best : null;
    }
}
