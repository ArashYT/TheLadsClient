package com.thelads.core.v1_8_9.feature.fastquit;

import com.thelads.core.v1_8_9.feature.fastquit.mixin.GuiSelectWorldAccessor;
import java.util.List;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.world.storage.SaveFormatComparator;

/**
 * The world list's saves for WorldListFastQuitMixin. The accessor cast lives here, outside the mixin: 3D Skin Layers ships an
 * older Mixin that, when it is the one Forge loads, cannot resolve another mixin's accessor inside GuiSelectWorld$List and
 * crashed the singleplayer world list.
 */
public final class WorldListSaves {
    private WorldListSaves() {}

    public static List<SaveFormatComparator> of(GuiSelectWorld screen) {
        return ((GuiSelectWorldAccessor) screen).lads$worlds();
    }
}
