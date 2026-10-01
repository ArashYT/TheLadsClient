// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.event;

import com.thelads.core.v1_21_11.embedded.controlling.api.events.ISetToDefaultEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * Fired when a key is set to the default, either through the individual reset button or the global reset button.
 */
public class SetToDefaultEvent implements ISetToDefaultEvent {
    
    private final Options options;
    private final KeyMapping mapping;
    
    public SetToDefaultEvent(Options options, KeyMapping mapping) {
        
        this.options = options;
        this.mapping = mapping;
    }
    
    @Override
    public Options options() {
        
        return options;
    }
    
    @Override
    public KeyMapping mapping() {
        
        return mapping;
    }
    
}
