// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.event;

import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;

/**
 * KeyEntryMouseReleasedEvent is called at the start of {@link IKeyEntry#mouseReleased(MouseClick)}.
 * <p>
 * If you are consuming this event, call {@link KeyEntryMouseReleasedEvent#handled(boolean)} with a value of {@code true}.
 */
public class KeyEntryMouseReleasedEvent implements IKeyEntryMouseReleasedEvent {
    
    private final IKeyEntry entry;
    private final MouseClick event;
    
    private boolean handled;
    
    public KeyEntryMouseReleasedEvent(IKeyEntry entry, MouseClick event) {
        
        this.entry = entry;
        this.event = event;
    }
    
    public IKeyEntry entry() {
        
        return entry;
    }
    
    @Override
    public MouseClick event() {
        
        return event;
    }
    
    public boolean handled() {
        
        return handled;
    }
    
    public void handled(boolean handled) {
        
        this.handled = handled;
    }
    
}
