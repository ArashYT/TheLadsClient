package com.thelads.core.v1_20_1.embedded.entityculling;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

/** The embedded Entity Culling mixins apply only while the original Entity Culling jar is not installed. */
public final class EntityCullingMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "EntityCulling"; }
    @Override protected String[] originals() { return new String[] {"entityculling"}; }
}
