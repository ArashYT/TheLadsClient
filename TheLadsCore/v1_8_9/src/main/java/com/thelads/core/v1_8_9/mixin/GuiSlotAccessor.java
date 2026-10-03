package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (Probe160s): a list's scroll as a float, and the pointer handleMouseInput checks (its last drawScreen's). */
@Mixin(GuiSlot.class)
public interface GuiSlotAccessor {
    @Accessor("amountScrolled") float ladsScroll();
    @Accessor("mouseX") void ladsSetMouseX(int x);
    @Accessor("mouseY") void ladsSetMouseY(int y);
}
