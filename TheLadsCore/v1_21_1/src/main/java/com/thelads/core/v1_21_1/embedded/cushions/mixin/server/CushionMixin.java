// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions.mixin.server;

import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v1_21_1.embedded.cushions.OptCushion;
import com.thelads.core.v1_21_1.embedded.cushions.server.CushionServerExt;

// Targets the Cushion-Backport entity by name (no compile dependency). Also stamps the
// OptCushion marker so the other mixins can recognise it via instanceof.
@Mixin(targets = "com.leclowndu93150.cushionbackport.entity.Cushion")
public abstract class CushionMixin implements CushionServerExt, OptCushion {
    @Shadow
    public abstract DyeColor getColor();

    @Shadow
    public abstract void setColor(DyeColor color);
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

    @Override
    public DyeColor optimizedcushions$color() {
        return this.getColor();
    }

    @Override
    public void optimizedcushions$setColor(final DyeColor color) {
        this.setColor(color);
    }
}
