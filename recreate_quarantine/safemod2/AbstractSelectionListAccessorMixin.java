package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
    @Accessor("entries")
    void setEntries(java.util.List<AbstractSelectionList.Entry> entries);
}
