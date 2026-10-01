// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.platform;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v26_2.embedded.controlling.api.event.ControllingEvents;
import com.thelads.core.v26_2.embedded.controlling.api.event.KeyEntryListenersEvent;
import com.thelads.core.v26_2.embedded.controlling.api.event.KeyEntryMouseClickedEvent;
import com.thelads.core.v26_2.embedded.controlling.api.event.KeyEntryMouseReleasedEvent;
import com.thelads.core.v26_2.embedded.controlling.api.event.KeyEntryRenderEvent;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.mojang.datafixers.util.Either;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Unit;

import java.util.List;

public class FabricEventHandler implements IEventHelper {
    
    @Override
    public Either<IKeyEntryListenersEvent, List<GuiEventListener>> fireKeyEntryListenersEvent(IKeyEntry entry) {
        
        KeyEntryListenersEvent event = new KeyEntryListenersEvent(entry);
        if(FabricHelper.isFabricLoaded()) {
            return Either.right(ControllingEvents.KEY_ENTRY_LISTENERS_EVENT.invoker().handle(event));
        }
        
        return Either.right(event.listeners());
    }
    
    @Override
    public Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, MouseButtonEvent event, boolean doubleClick) {
        
        KeyEntryMouseClickedEvent clickEvent = new KeyEntryMouseClickedEvent(entry, event, doubleClick);
        if(FabricHelper.isFabricLoaded()) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_CLICKED_EVENT.invoker().handle(clickEvent));
        }
        
        return Either.right(clickEvent.handled());
    }
    
    @Override
    public Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, MouseButtonEvent event) {
        
        KeyEntryMouseReleasedEvent releaseEvent = new KeyEntryMouseReleasedEvent(entry, event);
        if(FabricHelper.isFabricLoaded()) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_RELEASED_EVENT.invoker().handle(releaseEvent));
        }
        
        return Either.right(releaseEvent.handled());
    }
    
    @Override
    public Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphicsExtractor graphics, int x, int y, int rowLeft, int rowWidth, boolean hovered, float partialTicks) {
        
        if(FabricHelper.isFabricLoaded()) {
            return Either.right(ControllingEvents.KEY_ENTRY_RENDER_EVENT.invoker()
                    .handle(new KeyEntryRenderEvent(entry, graphics, x, y, rowLeft, rowWidth, hovered, partialTicks)));
        }
        return Either.right(Unit.INSTANCE);
    }
    
}
