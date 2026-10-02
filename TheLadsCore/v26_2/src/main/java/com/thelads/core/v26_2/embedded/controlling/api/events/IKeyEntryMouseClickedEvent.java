// Adapted from Controlling 26.2.4 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.events;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import net.minecraft.client.input.MouseButtonEvent;

public interface IKeyEntryMouseClickedEvent {
    
    IKeyEntry entry();
    
    MouseButtonEvent event();
    
    boolean doubleClick();
    
    boolean handled();
    
    void handled(boolean handled);
    
}
