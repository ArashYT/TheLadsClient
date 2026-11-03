package com.thelads.core.client.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * What drawing an account's real skin needs: vanilla's skin managers draw a bare GameProfile(id, name) with the default skin of its id, as
 * the skin is in the profile's textures property, which only the session server has. Each version supplies that call ({@code P} is its
 * profile, null when there is none) and {@link #get} never waits for it: one background thread, one lookup per id, the answer kept (the
 * skin file itself goes through vanilla's own disk cache). Until it is known a head shows the default skin.
 */
public final class SkinProfiles<P> {
    private static final long RECHECK_MS = 10 * 60_000L; // a skin changed in the launcher shows up while the game runs
    private static final long RETRY_MS = 60_000L;
    private static final int MAX_IDS = 512;

    private record Known<P>(P profile, long nextFetch) {}

    private final Function<UUID, P> fetch;
    private final Map<UUID, Known<P>> known = new ConcurrentHashMap<>();
    private final Set<UUID> asked = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Lads Skin Profiles");
        thread.setDaemon(true);
        return thread;
    });

    public SkinProfiles(Function<UUID, P> fetch) {
        this.fetch = fetch;
    }

    /** The profile with its textures, or null while it is not known. Offline ids (version 3, made from a name) have no profile, and are never asked for. */
    public P get(UUID id) {
        if (id.version() != 4) return null;
        Known<P> entry = known.get(id);
        if ((entry == null || System.currentTimeMillis() >= entry.nextFetch()) && asked.add(id)) {
            worker.execute(() -> lookUp(id));
        }
        return entry == null ? null : entry.profile();
    }

    private void lookUp(UUID id) {
        P profile = null;
        try {
            profile = fetch.apply(id);
        } catch (RuntimeException | LinkageError ignored) {
            // no connection or Mojang's rate limit: the default skin until the retry
        }
        if (known.size() > MAX_IDS) known.clear();
        long now = System.currentTimeMillis();
        Known<P> before = known.get(id);
        if (profile != null) known.put(id, new Known<>(profile, now + RECHECK_MS));
        else known.put(id, new Known<>(before == null ? null : before.profile(), now + RETRY_MS));
        asked.remove(id);
    }

    /** {@code uuid} of a head, the game's own id when the caller only knows the name and it is the game's own (title screen, HUD). */
    public static String orSession(String username, String uuid, String sessionName, UUID sessionId) {
        boolean noId = uuid == null || uuid.isBlank();
        return noId && sessionId != null && sessionName != null && sessionName.equalsIgnoreCase(username) ? sessionId.toString() : uuid;
    }
}
