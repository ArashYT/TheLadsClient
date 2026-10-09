package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor {
    @Accessor("key") KeyMapping ladsKey();
}
