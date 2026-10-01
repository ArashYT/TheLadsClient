// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.api;

import com.thelads.core.v1_21_1.embedded.controlling.api.entries.IKeyEntry;

import java.util.List;

public interface ISort {
    
    void sort(List<IKeyEntry> entries);
    
}
