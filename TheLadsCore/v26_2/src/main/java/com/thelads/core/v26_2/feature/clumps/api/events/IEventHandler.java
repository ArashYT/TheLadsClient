// Adapted from Clumps 26.2.1, Copyright (c) 2021 Jaredlll08, MIT.
package com.thelads.core.v26_2.feature.clumps.api.events;

public interface IEventHandler<T, U> {
    
    U handle(T event);
    
}
