// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.event;

import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;


/**
 * KeyEntryMouseClickedEvent is called at the start of {@link IKeyEntry#mouseClicked(MouseClick, boolean)}.
 * <p>
 * If you are consuming this event, call {@link KeyEntryMouseClickedEvent#handled(boolean)} with a value of {@code true}.
 */
public class KeyEntryMouseClickedEvent implements IKeyEntryMouseClickedEvent {
    
    private final IKeyEntry entry;
    private final MouseClick event;
    private final boolean doubleClick;
    private boolean handled;
    
    public KeyEntryMouseClickedEvent(IKeyEntry entry, MouseClick event, boolean doubleClick) {
        
        this.entry = entry;
        this.event = event;
        this.doubleClick = doubleClick;
    }
    
    public IKeyEntry entry() {
        
        return entry;
    }
    
    @Override
    public MouseClick event() {
        
        return event;
    }
    
    @Override
    public boolean doubleClick() {
        
        return doubleClick;
    }
    
    public boolean handled() {
        
        return handled;
    }
    
    public void handled(boolean handled) {
        
        this.handled = handled;
    }
    
}
