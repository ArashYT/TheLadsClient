package com.thelads.core.client.audio;

/** Audio Engine priority of a playing sound. Music and records are handled separately: the source pool never evicts them. */
public final class SoundPriority {
    public static final int MUSIC = 0, AMBIENT = 1, NEUTRAL = 2, HOSTILE = 3, CRITICAL = 4;

    private SoundPriority() {}

    /** By SoundSource name (the same on every version) and sound id path; dist is the distance to the listener in blocks. */
    public static int classify(String category, String path, double dist) {
        String c = category == null ? "" : category;
        String p = path == null ? "" : path;
        if (c.equals("MUSIC") || c.equals("RECORDS")) return MUSIC;
        if (c.equals("MASTER") || c.equals("PLAYERS") || c.equals("VOICE") || c.equals("UI") || p.startsWith("ui.")) return CRITICAL;
        if (p.contains("damage") || (p.contains("explode") && dist < 24.0)) return CRITICAL;
        if (c.equals("HOSTILE")) return HOSTILE;
        if (c.equals("AMBIENT") || c.equals("WEATHER")) return AMBIENT;
        return NEUTRAL; // BLOCKS, NEUTRAL and unknown categories
    }

    public static String name(int p) {
        switch (p) {
            case CRITICAL: return "critical";
            case HOSTILE: return "hostile";
            case NEUTRAL: return "block/neutral";
            case AMBIENT: return "ambient";
            default: return "music";
        }
    }
}
