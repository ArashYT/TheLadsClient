package com.thelads.core.v1_20_1.mixin.menus;

import java.util.Collection;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.20.1's list swap is protected; the Controls search replaces a list's entries with the filtered ones. */
@Mixin(AbstractSelectionList.class)
public interface SelectionListInvoker {
    @Invoker("replaceEntries") void ladsReplaceEntries(Collection<?> entries);
}
