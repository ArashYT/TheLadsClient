// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.platform;


import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Unit;

import java.util.List;

public interface IEventHelper {
    
    Either<IKeyEntryListenersEvent, List<GuiEventListener>> fireKeyEntryListenersEvent(IKeyEntry entry);
    
    Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, MouseButtonEvent event, boolean doubleClick);
    
    Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, MouseButtonEvent event);
    
    Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphics stack, int x, int y, int rowLeft, int rowWidth, boolean hovered, float partialTicks);
    
}
