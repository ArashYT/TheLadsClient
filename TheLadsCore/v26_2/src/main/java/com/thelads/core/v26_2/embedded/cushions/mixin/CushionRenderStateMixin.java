// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v26_2.embedded.cushions.CushionRenderStateExt;

@Mixin(targets = "com.leclowndu93150.cushionbackport.client.CushionRenderState")
public class CushionRenderStateMixin implements CushionRenderStateExt {
    @Unique
    private boolean optimizedcushions$baked;

    @Override
    public void optimizedcushions$setBaked(final boolean baked) {
        this.optimizedcushions$baked = baked;
    }

    @Override
    public boolean optimizedcushions$isBaked() {
        return this.optimizedcushions$baked;
    }
}
