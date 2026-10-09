// Adapted from Clumps 26.2.1 (Lads 26.3 copy), ported to Minecraft 1.20.1 by The Lads, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v1_20_1.feature.clumps;

import java.util.Map;

public interface ClumpedOrb {
    Map<Integer, Integer> ladsClumps$values();
    void ladsClumps$values(Map<Integer, Integer> values);
    boolean ladsClumps$managed();
}
