package com.thelads.core.v1_20_1.perf;

/**
 * Lads Particle Core's mixins (mixin/particlecore) apply only without the real Particle Core mod: ParticleCore1201 stands
 * down next to it anyway, and its hooks would still run per particle per frame on the same methods that mod hooks.
 */
public final class ParticleCoreMixinPlugin1201 extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "Particle Core"; }
    @Override protected String[] originals() { return new String[] {"particle_core", "particlecore", "particle-core"}; }
}
