package com.thelads.core.v1_21_11.mixin;

import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldSelectionList.WorldListEntry.class)
public interface WorldListEntryWidgetsAccessor {
    @Accessor("worldNameText")
    StringWidget ladsWorldNameText();

    @Accessor("idAndLastPlayedText")
    StringWidget ladsIdAndLastPlayedText();

    @Accessor("infoText")
    StringWidget ladsInfoText();
}
