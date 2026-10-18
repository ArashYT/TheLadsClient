package com.thelads.core.client.cosmetics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Who is emoting, shared by the animation side (poses, first-person arms, wheel) and the network side
 * (CosmeticsLink relays emotes to other Lads players on the same server). Times are System.currentTimeMillis().
 * Thread-safe: the network thread writes remote players, the render thread reads.
 */
public final class Emotes {
    /** One player's emote: id, when it started, and when it was asked to stop (0 while still playing). */
    public record Playing(String id, long startMs, long stopMs) {
        public boolean stopping() { return stopMs != 0; }
    }

    private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
    private static volatile BiConsumer<String, Long> outbound = (id, start) -> {};

    private Emotes() {}

    /** The network side listens for the local player's changes: (emote id or null for stop, start ms). */
    public static void setOutbound(BiConsumer<String, Long> sink) { outbound = sink == null ? (id, start) -> {} : sink; }

    /** Local player starts an emote (animation side calls this, e.g. from the emote wheel). */
    public static void playLocal(UUID self, String id) {
        long now = System.currentTimeMillis();
        PLAYING.put(self, new Playing(id, now, 0));
        outbound.accept(id, now);
    }

    /** Local player stops (moved, pressed again, or died): the pose blends out instead of cutting. */
    public static void stopLocal(UUID self) {
        if (stop(self)) outbound.accept(null, System.currentTimeMillis());
    }

    /** A remote player's emote state from the network; id null = stop. ageMs = how long it has been playing. */
    public static void onRemote(UUID player, String id, long ageMs) {
        if (id == null) { stop(player); return; }
        PLAYING.put(player, new Playing(id, System.currentTimeMillis() - Math.max(0, ageMs), 0));
    }

    /** Remote player stopped locally detected movement: same blend-out as a network stop. */
    public static boolean stop(UUID player) {
        Playing p = PLAYING.get(player);
        if (p == null || p.stopping()) return false;
        PLAYING.put(player, new Playing(p.id(), p.startMs(), System.currentTimeMillis()));
        return true;
    }

    public static Playing get(UUID player) { return player == null ? null : PLAYING.get(player); }

    /** Animation side drops a finished blend-out; network side drops everyone on disconnect/room change. */
    public static void remove(UUID player) { PLAYING.remove(player); }
    public static void clearRemote(UUID self) { PLAYING.keySet().removeIf(u -> !u.equals(self)); }
}
