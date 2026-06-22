/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.fabric.api.event.Event
 *  net.fabricmc.fabric.api.event.EventFactory
 */
package squeek.appleskin.api.handler;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface EventHandler<IEvent> {
    public static <T> Event<EventHandler<T>> createArrayBacked() {
        return EventFactory.createArrayBacked(EventHandler.class, listeners -> event -> {
            for (EventHandler listener : listeners) {
                listener.interact(event);
            }
        });
    }

    public void interact(IEvent var1);
}

