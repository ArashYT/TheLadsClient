// The Lads: the embedded Capes copy stands down while the original Capes mod is installed (Fabric or Forge jar).
package com.thelads.core.v1_20_1.embedded.capes;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class CapesMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "Capes"; }
    @Override protected String[] originals() { return new String[] {"capes"}; }
}
