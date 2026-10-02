// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.events;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import net.minecraft.client.input.MouseButtonEvent;

public interface IKeyEntryMouseReleasedEvent {
    
    IKeyEntry getEntry();
    
    MouseButtonEvent event();
    
    boolean isHandled();
    
    void setHandled(boolean handled);
    
}
