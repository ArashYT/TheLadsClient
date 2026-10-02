// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.api.events;

import com.thelads.core.v1_21_1.embedded.controlling.api.entries.IKeyEntry;

public interface IKeyEntryMouseClickedEvent {
    
    IKeyEntry getEntry();
    
    double getMouseX();
    
    double getMouseY();
    
    int getButtonId();
    
    boolean isHandled();
    
    void setHandled(boolean handled);
    
}
