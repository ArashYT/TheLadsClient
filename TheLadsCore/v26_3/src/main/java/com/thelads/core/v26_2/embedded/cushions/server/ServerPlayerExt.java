// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.server;

/** Duck interface on {@code ServerPlayer}: throttles cushion tracker re-evaluation. */
public interface ServerPlayerExt {
    /**
     * True when cushion visibility for this player was evaluated recently enough
     * (moved less than a block and less than a second ago) to skip this pass.
     * When it returns false the evaluation is recorded as happening now.
     */
    boolean optimizedcushions$skipCushionTracking(long gameTime);
}
