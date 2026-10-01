// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.controlling.api.event;

import com.thelads.core.v26_2.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v26_2.embedded.controlling.api.events.IKeyEntryRenderEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * RenderKeyEntryEvent is called at the top of {@link IKeyEntry#extractContent(GuiGraphicsExtractor, int, int, boolean, float)}
 * is called, allowing mods to render additional info.
 */
public record KeyEntryRenderEvent(IKeyEntry entry, GuiGraphicsExtractor graphics, int x, int y, int rowLeft,
                                  int rowWidth, boolean hovered, float partialTicks) implements IKeyEntryRenderEvent {
    
}
