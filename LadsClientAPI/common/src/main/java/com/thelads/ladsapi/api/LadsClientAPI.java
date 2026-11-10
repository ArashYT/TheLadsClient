package com.thelads.ladsapi.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API for other plugins/mods: LadsClientAPI.get().player(uuid).ifPresent(p -> p.limit("Fullbright", "Brightness", 0, 10)).
 * FROZEN INTERFACE (phase 0); lane SRV-A implements it in common.
 */
public interface LadsClientAPI {
    /** The running instance, or null before the jar started. */
    static LadsClientAPI get() {
        return Holder.api;
    }

    /** A connected player who answered the hello (runs the Lads Client), or empty. */
    Optional<LadsPlayer> player(UUID uuid);

    /** Whether this player's client proved itself through the Lads relay: what perks and gates should ask ({@link LadsPlayer#verified}). */
    default boolean verified(UUID uuid) {
        return player(uuid).map(LadsPlayer::verified).orElse(false);
    }

    /**
     * Whether this player gets the server's Lads Client perks: the one call a perk plugin makes. A verified client; or any client
     * that answered the hello while this server does not check (verify-client off) or has `perks-need-verified: false`, the time
     * before every player has a client that can prove itself.
     */
    default boolean perks(UUID uuid) {
        return verified(uuid);
    }

    /** Lane SRV-A sets this when the core starts. */
    final class Holder {
        static volatile LadsClientAPI api;

        private Holder() {}

        public static void set(LadsClientAPI value) {
            api = value;
        }
    }
}
