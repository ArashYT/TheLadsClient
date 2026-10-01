// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import net.minecraft.client.input.MouseButtonEvent;


/**
 * KeyEntryMouseClickedEvent is called at the start of {@link IKeyEntry#mouseClicked(MouseButtonEvent, boolean)}.
 * <p>
 * If you are consuming this event, call {@link KeyEntryMouseClickedEvent#handled(boolean)} with a value of {@code true}.
 */
public class KeyEntryMouseClickedEvent implements IKeyEntryMouseClickedEvent {
    
    private final IKeyEntry entry;
    private final MouseButtonEvent event;
    private final boolean doubleClick;
    private boolean handled;
    
    public KeyEntryMouseClickedEvent(IKeyEntry entry, MouseButtonEvent event, boolean doubleClick) {
        
        this.entry = entry;
        this.event = event;
        this.doubleClick = doubleClick;
    }
    
    public IKeyEntry entry() {
        
        return entry;
    }
    
    @Override
    public MouseButtonEvent event() {
        
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
