// Adapted from Clumps 26.2.1, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v26_2.feature.clumps;

import java.util.Map;

public interface ClumpedOrb {
    Map<Integer, Integer> ladsClumps$values();
    void ladsClumps$values(Map<Integer, Integer> values);
    boolean ladsClumps$managed();
}
