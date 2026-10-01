// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.events.IHasConflictingModifierEvent;
import net.minecraft.client.KeyMapping;

/**
 * Fired to check if a {@link KeyMapping} conflicts with another {@link KeyMapping}.
 */
public record HasConflictingModifierEvent(KeyMapping thisMapping,
                                          KeyMapping otherMapping) implements IHasConflictingModifierEvent {
    
    
}
