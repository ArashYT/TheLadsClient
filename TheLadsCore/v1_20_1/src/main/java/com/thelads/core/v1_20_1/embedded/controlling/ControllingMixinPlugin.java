// The Lads: Controlling and its Searchables copy stand down while the original Controlling is installed (Fabric or Forge jar).
package com.thelads.core.v1_20_1.embedded.controlling;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class ControllingMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "Controlling"; }
    @Override protected String[] originals() { return new String[] {"controlling"}; }
}
