package com.thelads.core.v1_8_9.feature.fastquit.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.world.storage.SaveFormatComparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiSelectWorld.class)
public interface GuiSelectWorldAccessor {
    @Accessor("field_146639_s") List<SaveFormatComparator> lads$worlds();
}
