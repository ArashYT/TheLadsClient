// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.platform;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.api.event.ControllingEvents;
import com.thelads.core.v1_21_11.embedded.controlling.api.event.KeyEntryListenersEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.event.KeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.event.KeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.event.KeyEntryRenderEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.thelads.core.v1_21_11.embedded.controlling.client.NewKeyBindsList;
import com.mojang.datafixers.util.Either;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Unit;

import java.util.List;

public class FabricEventHandler implements IEventHelper {
    
    @Override
    public Either<IKeyEntryListenersEvent, List<GuiEventListener>> fireKeyEntryListenersEvent(IKeyEntry entry) {
        KeyEntryListenersEvent event = new KeyEntryListenersEvent(entry);
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_LISTENERS_EVENT.invoker().handle(event));
        }
    
        return Either.right(event.getListeners());
    }
    
    @Override
    public Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, MouseButtonEvent event, boolean doubleClick) {
        KeyEntryMouseClickedEvent clickEvent = new KeyEntryMouseClickedEvent(entry, event, doubleClick);
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_CLICKED_EVENT.invoker().handle(clickEvent));
        }
        
        return Either.right(clickEvent.isHandled());
    }
    
    @Override
    public Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, MouseButtonEvent event) {
        KeyEntryMouseReleasedEvent releaseEvent = new KeyEntryMouseReleasedEvent(entry, event);
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_RELEASED_EVENT.invoker().handle(releaseEvent));
        }
        
        return Either.right(releaseEvent.isHandled());
    }
    
    @Override
    public Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphics guiGraphics, int y, int x, int rowLeft, int rowWidth, boolean hovered, float partialTicks) {
        
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_RENDER_EVENT.invoker()
                    .handle(new KeyEntryRenderEvent(entry, guiGraphics, x, y, rowLeft, rowWidth, hovered, partialTicks)));
        }
        return Either.right(Unit.INSTANCE);
    }
}
