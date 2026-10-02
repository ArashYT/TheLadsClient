// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryListenersEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * GetKeyEntryListenersEvent is called to get the values for {@link IKeyEntry#children()}.
 * Allowing for mods to add more listeners.
 */
public record KeyEntryListenersEvent(IKeyEntry entry,
                                     List<GuiEventListener> listeners) implements IKeyEntryListenersEvent {
    
    public KeyEntryListenersEvent(IKeyEntry entry, List<GuiEventListener> listeners) {
        
        this.entry = entry;
        this.listeners = listeners;
        listeners().add(entry.getBtnChangeKeyBinding());
        listeners().add(entry.getBtnResetKeyBinding());
    }
    
    public KeyEntryListenersEvent(IKeyEntry entry) {
        
        this(entry, new ArrayList<>());
        
    }
    
}
