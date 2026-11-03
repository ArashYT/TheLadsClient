package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A world row's folder name, for its menu's Open folder. */
@Mixin(WorldSelectionList.WorldListEntry.class)
public interface WorldListEntryAccessor {
    @Accessor("summary") LevelSummary ladsSummary();
}
