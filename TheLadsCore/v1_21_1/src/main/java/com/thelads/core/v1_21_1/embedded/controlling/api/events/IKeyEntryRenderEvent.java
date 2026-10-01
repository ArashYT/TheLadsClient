// Adapted from Controlling 19.0.5 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.controlling.api.events;

import com.thelads.core.v1_21_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_1.embedded.controlling.client.NewKeyBindsList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

public interface IKeyEntryRenderEvent {
    
    IKeyEntry getEntry();
    
    GuiGraphics getGuiGraphics();
    
    int getSlotIndex();
    
    int getY();
    
    int getX();
    
    int getRowLeft();
    
    int getRowWidth();
    
    int getMouseX();
    
    int getMouseY();
    
    boolean isHovered();
    
    float getPartialTicks();
    
}
