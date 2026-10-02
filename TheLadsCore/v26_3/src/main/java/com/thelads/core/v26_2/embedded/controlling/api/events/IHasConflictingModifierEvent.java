// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.events;

import net.minecraft.client.KeyMapping;

public interface IHasConflictingModifierEvent {
    
    KeyMapping thisMapping();
    
    KeyMapping otherMapping();
    
}
