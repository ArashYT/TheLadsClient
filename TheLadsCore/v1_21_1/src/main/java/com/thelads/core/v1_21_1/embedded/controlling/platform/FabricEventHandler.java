// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.platform;

import com.thelads.core.v1_21_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_1.embedded.controlling.api.event.ControllingEvents;
import com.thelads.core.v1_21_1.embedded.controlling.api.event.KeyEntryListenersEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.event.KeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.event.KeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.event.KeyEntryRenderEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.thelads.core.v1_21_1.embedded.controlling.client.NewKeyBindsList;
import com.mojang.datafixers.util.Either;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
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
    public Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, double mouseX, double mouseY, int buttonId) {
        KeyEntryMouseClickedEvent event = new KeyEntryMouseClickedEvent(entry, mouseX, mouseY, buttonId);
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_CLICKED_EVENT.invoker().handle(event));
        }
        
        return Either.right(event.isHandled());
    }
    
    @Override
    public Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, double mouseX, double mouseY, int buttonId) {
        KeyEntryMouseReleasedEvent event = new KeyEntryMouseReleasedEvent(entry, mouseX, mouseY, buttonId);
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_MOUSE_RELEASED_EVENT.invoker().handle(event));
        }
        
        return Either.right(event.isHandled());
    }
    
    @Override
    public Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphics guiGraphics, int slotIndex, int y, int x, int rowLeft, int rowWidth, int mouseX, int mouseY, boolean hovered, float partialTicks) {
        
        if(FabricLoader.getInstance().isModLoaded("fabric")) {
            return Either.right(ControllingEvents.KEY_ENTRY_RENDER_EVENT.invoker().handle(new KeyEntryRenderEvent(entry, guiGraphics, slotIndex, y, x, rowLeft, rowWidth, mouseX, mouseY, hovered, partialTicks)));
        }
        return Either.right(Unit.INSTANCE);
    }
}
