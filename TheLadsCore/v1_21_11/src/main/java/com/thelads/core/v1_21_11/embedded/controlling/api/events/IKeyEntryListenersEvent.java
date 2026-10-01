// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.events;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.client.NewKeyBindsList;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

public interface IKeyEntryListenersEvent {
    
    List<GuiEventListener> getListeners();
    
    IKeyEntry getEntry();
    
}
