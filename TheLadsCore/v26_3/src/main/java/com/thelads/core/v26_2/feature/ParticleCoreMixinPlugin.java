package com.thelads.core.v26_2.feature;

import com.thelads.core.v26_2.embedded.EmbeddedMods;
import com.thelads.core.v26_2.embedded.EmbeddedMixinPlugin;

/**
 * Lads Particle Core's mixins (mixin/particlecore) apply only without the real Particle Core mod: NativeParticleCore stands
 * down next to it anyway, and its hooks would still run per particle per frame on the same methods that mod hooks.
 */
public final class ParticleCoreMixinPlugin extends EmbeddedMixinPlugin {
    @Override protected String originalModId() { return "particle_core"; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return super.shouldApplyMixin(targetClassName, mixinClassName) && EmbeddedMods.active("particlecore") && EmbeddedMods.active("particle-core");
    }
}
