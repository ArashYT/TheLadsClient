// Adapted from Controlling 29.0.1 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.controlling.api.entries;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface IKeyEntry {
    
    Component categoryName();
    
    KeyMapping getKey();
    
    Component getKeyDesc();
    
    Button getBtnResetKeyBinding();
    
    Button getBtnChangeKeyBinding();
    
    List<GuiEventListener> children();
    
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick);
    
    boolean mouseReleased(MouseButtonEvent event);
    
    void renderContent(GuiGraphics guiGraphics, int x, int y, boolean hovered, float partialTicks);
    
}
