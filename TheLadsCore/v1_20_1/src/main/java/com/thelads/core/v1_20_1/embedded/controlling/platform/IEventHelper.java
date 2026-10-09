// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.platform;


import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.thelads.core.v1_20_1.embedded.controlling.api.MouseClick;
import net.minecraft.util.Unit;

import java.util.List;

public interface IEventHelper {
    
    Either<IKeyEntryListenersEvent, List<GuiEventListener>> fireKeyEntryListenersEvent(IKeyEntry entry);
    
    Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, MouseClick event, boolean doubleClick);
    
    Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, MouseClick event);
    
    Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphics graphics, int x, int y, int rowLeft, int rowWidth, boolean hovered, float partialTicks);
    
}
