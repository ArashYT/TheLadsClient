package com.thelads.core.v26_2.social;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server-safe state for P2P world hosting (no net.minecraft.client.* imports).
 * Accessed safely by server-side mixins (ServerConnectionListenerMixin, PlayerListMixin).
 */
public final class P2PHostState26 {
    private static volatile boolean loopbackScope = false;
    private static volatile Predicate<UUID> loginFilter = null;
    private static final Set<UUID> mutedGuests = Collections.synchronizedSet(new HashSet<>());

    private P2PHostState26() {}

    public static boolean isLoopbackScope() {
        return loopbackScope;
    }

    public static void setLoopbackScope(boolean enabled) {
        loopbackScope = enabled;
    }

    public static Predicate<UUID> getLoginFilter() {
        return loginFilter;
    }

    public static void setLoginFilter(Predicate<UUID> filter) {
        loginFilter = filter;
    }

    public static boolean isMuted(UUID id) {
        return id != null && mutedGuests.contains(id);
    }

    public static void muteGuest(UUID id) {
        if (id != null) mutedGuests.add(id);
    }

    public static void clearMutedGuests() {
        mutedGuests.clear();
    }

    public static void reset() {
        loopbackScope = false;
        loginFilter = null;
        mutedGuests.clear();
    }
}
