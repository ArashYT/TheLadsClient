// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.event;

import com.thelads.core.v1_21_11.embedded.controlling.api.events.IHasConflictingModifierEvent;
import net.minecraft.client.KeyMapping;

/**
 * Fired to check if a {@link KeyMapping} conflicts with another {@link KeyMapping}.
 */
public class HasConflictingModifierEvent implements IHasConflictingModifierEvent {
    
    private final KeyMapping thisMapping;
    private final KeyMapping otherMapping;
    
    public HasConflictingModifierEvent(KeyMapping thisMapping, KeyMapping otherMapping) {
        
        this.thisMapping = thisMapping;
        this.otherMapping = otherMapping;
    }
    
    @Override
    public KeyMapping thisMapping() {
        
        return thisMapping;
    }
    
    @Override
    public KeyMapping otherMapping() {
        
        return otherMapping;
    }
    
    
}
