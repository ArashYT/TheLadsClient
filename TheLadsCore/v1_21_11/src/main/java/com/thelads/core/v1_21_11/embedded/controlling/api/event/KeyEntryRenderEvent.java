// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.event;

import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.api.events.IKeyEntryRenderEvent;
import net.minecraft.client.gui.GuiGraphics;

/**
 * RenderKeyEntryEvent is called at the top of {@link IKeyEntry#renderContent(GuiGraphics, int, int, boolean, float)}
 * is called, allowing mods to render additional info.
 */
public class KeyEntryRenderEvent implements IKeyEntryRenderEvent {
    
    private final IKeyEntry entry;
    
    private final GuiGraphics guiGraphics;
    private final int y;
    private final int x;
    private final int rowLeft;
    private final int rowWidth;
    private final boolean hovered;
    private final float partialTicks;
    
    public KeyEntryRenderEvent(IKeyEntry entry, GuiGraphics guiGraphics, int x, int y, int rowLeft, int rowWidth, boolean hovered, float partialTicks) {
        
        this.entry = entry;
        this.guiGraphics = guiGraphics;
        this.y = y;
        this.x = x;
        this.rowLeft = rowLeft;
        this.rowWidth = rowWidth;
        this.hovered = hovered;
        this.partialTicks = partialTicks;
    }
    
    public IKeyEntry getEntry() {
        
        return entry;
    }
    
    public GuiGraphics getGuiGraphics() {
        
        return guiGraphics;
    }
    
    public int getY() {
        
        return y;
    }
    
    public int getX() {
        
        return x;
    }
    
    public int getRowLeft() {
        
        return rowLeft;
    }
    
    public int getRowWidth() {
        
        return rowWidth;
    }
    
    public boolean isHovered() {
        
        return hovered;
    }
    
    public float getPartialTicks() {
        
        return partialTicks;
    }
    
}
