// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.platform;


import com.thelads.core.v1_21_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryListenersEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryMouseClickedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryMouseReleasedEvent;
import com.thelads.core.v1_21_1.embedded.controlling.api.events.IKeyEntryRenderEvent;
import com.thelads.core.v1_21_1.embedded.controlling.client.NewKeyBindsList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.util.Unit;

import java.util.List;

public interface IEventHelper {
    
    Either<IKeyEntryListenersEvent, List<GuiEventListener>> fireKeyEntryListenersEvent(IKeyEntry entry);
    
    Either<IKeyEntryMouseClickedEvent, Boolean> fireKeyEntryMouseClickedEvent(IKeyEntry entry, double mouseX, double mouseY, int buttonId);
    
    Either<IKeyEntryMouseReleasedEvent, Boolean> fireKeyEntryMouseReleasedEvent(IKeyEntry entry, double mouseX, double mouseY, int buttonId);
    
    Either<IKeyEntryRenderEvent, Unit> fireKeyEntryRenderEvent(IKeyEntry entry, GuiGraphics stack, int slotIndex, int y, int x, int rowLeft, int rowWidth, int mouseX, int mouseY, boolean hovered, float partialTicks);
    
}
