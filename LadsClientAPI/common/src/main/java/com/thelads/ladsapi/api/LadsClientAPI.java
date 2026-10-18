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

    /** Lane SRV-A sets this when the core starts. */
    final class Holder {
        static volatile LadsClientAPI api;

        private Holder() {}

        public static void set(LadsClientAPI value) {
            api = value;
        }
    }
}
