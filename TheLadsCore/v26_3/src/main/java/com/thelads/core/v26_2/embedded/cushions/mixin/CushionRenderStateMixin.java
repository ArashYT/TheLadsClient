// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import net.minecraft.client.renderer.entity.state.CushionRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v26_2.embedded.cushions.CushionRenderStateExt;

@Mixin(CushionRenderState.class)
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
