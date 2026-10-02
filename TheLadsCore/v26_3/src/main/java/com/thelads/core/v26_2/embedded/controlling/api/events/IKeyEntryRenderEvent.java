// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.events;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IKeyEntryRenderEvent {
    
    IKeyEntry entry();
    
    GuiGraphicsExtractor graphics();
    
    int x();
    
    int y();
    
    int rowLeft();
    
    int rowWidth();
    
    boolean hovered();
    
    float partialTicks();
    
}
