package com.thelads.core.v26_2.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSelectionList.class)
public interface AbstractSelectionListAccessor {
    @Invoker("repositionEntries")
    void ladsInvokeRepositionEntries();
}
