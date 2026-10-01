// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin.server;

import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v26_2.embedded.cushions.server.CushionServerExt;

@Mixin(Cushion.class)
public class CushionMixin implements CushionServerExt {
    @Unique
    private boolean optimizedcushions$serverTicking;

    @Unique
    private boolean optimizedcushions$inTicker;

    @Override
    public boolean optimizedcushions$isServerTicking() {
        return this.optimizedcushions$serverTicking;
    }

    @Override
    public void optimizedcushions$setServerTicking(final boolean ticking) {
        this.optimizedcushions$serverTicking = ticking;
    }

    @Override
    public boolean optimizedcushions$isInTicker() {
        return this.optimizedcushions$inTicker;
    }

    @Override
    public void optimizedcushions$setInTicker(final boolean inTicker) {
        this.optimizedcushions$inTicker = inTicker;
    }
}
