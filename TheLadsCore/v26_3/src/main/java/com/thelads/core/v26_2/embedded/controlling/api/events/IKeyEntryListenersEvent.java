// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.events;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

public interface IKeyEntryListenersEvent {
    
    List<GuiEventListener> listeners();
    
    IKeyEntry entry();
    
}
