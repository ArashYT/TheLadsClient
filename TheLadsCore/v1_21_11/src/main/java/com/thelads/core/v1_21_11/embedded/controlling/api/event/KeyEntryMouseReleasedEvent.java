// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.event;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * KeyEntryMouseReleasedEvent is called at the start of {@link IKeyEntry#mouseReleased(MouseButtonEvent)}.
 * <p>
 * If you are consuming this event, call {@link KeyEntryMouseReleasedEvent#setHandled(boolean)} with a value of {@code true}.
 */
public class KeyEntryMouseReleasedEvent implements IKeyEntryMouseReleasedEvent {
    
    private final IKeyEntry entry;
    private final MouseButtonEvent event;
    
    private boolean handled;
    
    public KeyEntryMouseReleasedEvent(IKeyEntry entry, MouseButtonEvent event) {
        
        this.entry = entry;
        this.event = event;
    }
    
    public IKeyEntry getEntry() {
        
        return entry;
    }
    
    @Override
    public MouseButtonEvent event() {
        
        return event;
    }
    
    public boolean isHandled() {
        
        return handled;
    }
    
    public void setHandled(boolean handled) {
        
        this.handled = handled;
    }
    
}
