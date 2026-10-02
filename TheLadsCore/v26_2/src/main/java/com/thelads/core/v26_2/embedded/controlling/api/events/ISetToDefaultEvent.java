// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.events;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

public interface ISetToDefaultEvent {
    
    Options options();
    
    KeyMapping mapping();
    
}
