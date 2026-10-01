// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.events;

import net.minecraft.client.KeyMapping;

public interface IHasConflictingModifierEvent {
    
    KeyMapping thisMapping();
    
    KeyMapping otherMapping();
    
}
