// Adapted from Clumps 26.2.1, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v26_2.feature.clumps.api.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class ClumpsEvents {
    
    public static final Event<IEventHandler<ValueEvent, Void>> VALUE_EVENT = EventFactory.createArrayBacked(IEventHandler.class, listeners -> event -> {
        for(IEventHandler<ValueEvent, Void> listener : listeners) {
            listener.handle(event);
        }
        return null;
    });
    
    public static final Event<IEventHandler<RepairEvent, Void>> REPAIR_EVENT = EventFactory.createArrayBacked(IEventHandler.class, listeners -> event -> {
        for(IEventHandler<RepairEvent, Void> listener : listeners) {
            listener.handle(event);
        }
        return null;
    });
    
}
