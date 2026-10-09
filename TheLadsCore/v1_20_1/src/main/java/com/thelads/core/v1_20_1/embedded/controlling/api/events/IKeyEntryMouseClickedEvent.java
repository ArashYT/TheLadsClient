// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.events;

import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;

public interface IKeyEntryMouseClickedEvent {
    
    IKeyEntry entry();
    
    MouseClick event();
    
    boolean doubleClick();
    
    boolean handled();
    
    void handled(boolean handled);
    
}
