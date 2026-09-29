// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v26_2.feature.food.api.handler;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface EventHandler<IEvent>
{
	static <T> Event<EventHandler<T>> createArrayBacked()
	{
		return EventFactory.createArrayBacked(EventHandler.class, listeners -> event -> {
			for (EventHandler listener : listeners)
			{
				listener.interact(event);
			}
		});
	}

	void interact(IEvent event);
}
