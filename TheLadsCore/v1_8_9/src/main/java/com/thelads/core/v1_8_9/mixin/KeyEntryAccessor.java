package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiKeyBindingList;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Controls search (ControlsScreen189): the key binding of a Controls row. */
@Mixin(GuiKeyBindingList.KeyEntry.class)
public interface KeyEntryAccessor {
    @Accessor("keybinding") KeyBinding getKeybinding();
}
