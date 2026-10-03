package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiKeyBindingList;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Controls search (ControlsScreen189): where Controls goes back to, and its key list swapped for the filtered one. */
@Mixin(GuiControls.class)
public interface GuiControlsAccessor {
    @Accessor GuiScreen getParentScreen();
    @Accessor void setKeyBindingList(GuiKeyBindingList list);
}
