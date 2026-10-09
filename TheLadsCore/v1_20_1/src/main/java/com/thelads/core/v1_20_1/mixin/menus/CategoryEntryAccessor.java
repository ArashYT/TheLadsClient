package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsList.CategoryEntry.class)
public interface CategoryEntryAccessor {
    @Accessor("name") Component ladsName();
}
