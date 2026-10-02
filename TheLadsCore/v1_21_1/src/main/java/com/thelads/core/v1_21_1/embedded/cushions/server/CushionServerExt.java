// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions.server;

import net.minecraft.world.item.DyeColor;

/** Duck interface on the backport Cushion: where the server side currently ticks it. */
public interface CushionServerExt {
    /** True between onTickingStart and onTickingEnd on the server. */
    boolean optimizedcushions$isServerTicking();

    void optimizedcushions$setServerTicking(boolean ticking);

    /** True while owned by {@link CushionServerTicker} instead of the vanilla entity tick list. */
    boolean optimizedcushions$isInTicker();

    void optimizedcushions$setInTicker(boolean inTicker);

    /** Forwards to the backport entity's dye colour (no compile dependency on it). */
    DyeColor optimizedcushions$color();

    void optimizedcushions$setColor(DyeColor color);
}
