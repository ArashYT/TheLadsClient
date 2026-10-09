// The Lads: World Play Time Reborn's embedded copy stands down while the original mod is installed (Fabric or Forge jar).
package com.thelads.core.v1_20_1.embedded.playtime;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class PlayTimeMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "PlayTime"; }
    @Override protected String[] originals() { return new String[] {"worldplaytimereborn", "worldplaytime"}; }
}
