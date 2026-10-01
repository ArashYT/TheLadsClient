// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.event;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_21_11.embedded.controlling.client.NewKeyBindsList;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * GetKeyEntryListenersEvent is called to get the values for {@link IKeyEntry#children()}.
 * Allowing for mods to add more listeners.
 */
public class KeyEntryListenersEvent implements IKeyEntryListenersEvent {
    
    private final IKeyEntry entry;
    
    private final List<GuiEventListener> listeners;
    
    public KeyEntryListenersEvent(IKeyEntry entry) {
        
        this.entry = entry;
        this.listeners = new ArrayList<>();
        
        getListeners().add(entry.getBtnChangeKeyBinding());
        getListeners().add(entry.getBtnResetKeyBinding());
    }
    
    
    public List<GuiEventListener> getListeners() {
        
        return listeners;
    }
    
    public IKeyEntry getEntry() {
        
        return entry;
    }
    
}
