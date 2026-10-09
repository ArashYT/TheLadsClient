// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.events;

import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import net.minecraft.client.gui.GuiGraphics;

public interface IKeyEntryRenderEvent {
    
    IKeyEntry entry();
    
    GuiGraphics graphics();
    
    int x();
    
    int y();
    
    int rowLeft();
    
    int rowWidth();
    
    boolean hovered();
    
    float partialTicks();
    
}
