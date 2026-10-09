// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api.event;

import com.thelads.core.v1_20_1.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_20_1.embedded.controlling.api.events.IKeyEntryRenderEvent;
import net.minecraft.client.gui.GuiGraphics;

/**
 * RenderKeyEntryEvent is called at the top of the key entry's render method
 * is called, allowing mods to render additional info.
 */
public record KeyEntryRenderEvent(IKeyEntry entry, GuiGraphics graphics, int x, int y, int rowLeft,
                                  int rowWidth, boolean hovered, float partialTicks) implements IKeyEntryRenderEvent {
    
}
