package com.thelads.core.v1_20_1.utility.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pack Profiles: the pack screen's pending Selected (top first) and Available lists. */
@Mixin(PackSelectionModel.class)
public interface PackModelAccessor1201 {
    @Accessor("selected") List<Pack> lads$selected();
    @Accessor("unselected") List<Pack> lads$unselected();
}
